# -*- coding: utf-8 -*-
import math

from odoo import api, fields, models


class FieldSalesLocationConfig(models.Model):
    _name = 'field.sales.location.config'
    _description = 'Configuración de Ubicación en Vivo - App móvil'

    name = fields.Char(string='Nombre', default='Configuración de Ubicación en Vivo')
    active_feature = fields.Boolean(
        string='Habilitar ubicación en vivo', default=False,
        help='Si está activo, la app envía la ubicación del vendedor periódicamente '
             '(incluso en segundo plano) para que el supervisor pueda ver su posición '
             'y la ruta recorrida en el día.',
    )
    ping_interval_seconds = fields.Integer(
        string='Intervalo de envío (segundos)', default=60,
        help='Cada cuánto envía la app su ubicación. Un intervalo más corto da una '
             'sensación más "en vivo" pero consume más batería y datos.',
    )
    retention_days = fields.Integer(
        string='Días de historial a conservar', default=30,
        help='Los registros de ubicación más antiguos que esto se eliminan automáticamente '
             '(tarea programada diaria) para no crecer la base de datos indefinidamente.',
    )
    offline_threshold_minutes = fields.Integer(
        string='Minutos para considerar "sin señal"', default=10,
        help='Si un vendedor no manda ubicación por más de este tiempo, el mapa en vivo lo '
             'marca como "sin señal" en vez de mostrar su última posición como si fuera actual.',
    )
    geofence_radius_meters = fields.Integer(
        string='Radio de geocerca por cliente (metros)', default=150,
        help='Si el vendedor entra a este radio de un cliente de su ruta del día (y aún no lo '
             'visitó), se genera un evento de "llegada" visible en el mapa en vivo.',
    )
    low_battery_threshold = fields.Integer(
        string='Umbral de batería baja (%)', default=20,
        help='Si la batería del celular reportada en el último ping está por debajo de este '
             'porcentaje, se marca al vendedor con una alerta de batería baja.',
    )

    @api.model
    def get_active_config(self):
        config = self.search([], limit=1)
        if not config:
            config = self.create({})
        return config

    @api.model
    def _cron_purgar_pings_antiguos(self):
        config = self.get_active_config()
        dias = config.retention_days or 30
        limite = fields.Datetime.now() - __import__('datetime').timedelta(days=dias)
        pings_viejos = self.env['field.sales.location.ping'].sudo().search([
            ('captured_at', '<', limite),
        ])
        pings_viejos.unlink()
        eventos_viejos = self.env['field.sales.geofence.event'].sudo().search([
            ('entered_at', '<', limite),
        ])
        eventos_viejos.unlink()


class FieldSalesLocationPing(models.Model):
    _name = 'field.sales.location.ping'
    _description = 'Registro de ubicación - App móvil'
    _order = 'captured_at desc'

    employee_id = fields.Many2one(
        'hr.employee', string='Vendedor', required=True, index=True,
        domain=[('is_mobile_seller', '=', True)],
    )
    latitude = fields.Float(string='Latitud', digits=(10, 7), required=True)
    longitude = fields.Float(string='Longitud', digits=(10, 7), required=True)
    accuracy = fields.Float(string='Precisión (m)')
    battery_level = fields.Integer(string='Batería (%)')
    captured_at = fields.Datetime(string='Capturado el', required=True, default=fields.Datetime.now, index=True)

    @api.model
    def _distancia_metros(self, lat1, lon1, lat2, lon2):
        """Distancia entre dos puntos GPS (fórmula de Haversine), en metros."""
        r = 6371000  # radio de la Tierra en metros
        p1, p2 = math.radians(lat1), math.radians(lat2)
        dp = math.radians(lat2 - lat1)
        dl = math.radians(lon2 - lon1)
        a = math.sin(dp / 2) ** 2 + math.cos(p1) * math.cos(p2) * math.sin(dl / 2) ** 2
        return r * 2 * math.atan2(math.sqrt(a), math.sqrt(1 - a))

    @api.model
    def _detectar_geocerca(self, employee, ping):
        """Si el vendedor entró al radio de un cliente de su ruta de hoy (y aún no lo visitó),
        crea un evento de geocerca (deduplicado por vendedor+cliente+día)."""
        config = self.env['field.sales.location.config'].sudo().get_active_config()
        radio = config.geofence_radius_meters or 150
        hoy = fields.Date.context_today(self)

        paradas = self.env['field.sales.route.stop'].sudo().search([
            ('employee_id', '=', employee.id),
            ('planned_date', '=', hoy),
        ])
        if not paradas:
            return

        Visit = self.env['field.sales.visit'].sudo()
        Event = self.env['field.sales.geofence.event'].sudo()

        for parada in paradas:
            cliente = parada.customer_id
            if not cliente.latitude and not cliente.longitude:
                continue

            ya_visitado = Visit.search_count([
                ('employee_id', '=', employee.id),
                ('customer_id', '=', cliente.id),
                ('date_visit', '>=', '%s 00:00:00' % hoy),
                ('date_visit', '<=', '%s 23:59:59' % hoy),
            ])
            if ya_visitado:
                continue

            distancia = self._distancia_metros(ping.latitude, ping.longitude, cliente.latitude, cliente.longitude)
            if distancia > radio:
                continue

            ya_notificado = Event.search_count([
                ('employee_id', '=', employee.id),
                ('customer_id', '=', cliente.id),
                ('entered_at', '>=', '%s 00:00:00' % hoy),
            ])
            if ya_notificado:
                continue

            Event.create({
                'employee_id': employee.id,
                'customer_id': cliente.id,
                'entered_at': fields.Datetime.now(),
                'distance_meters': distancia,
            })


class FieldSalesGeofenceEvent(models.Model):
    _name = 'field.sales.geofence.event'
    _description = 'Evento de geocerca - App móvil'
    _order = 'entered_at desc'

    employee_id = fields.Many2one('hr.employee', string='Vendedor', required=True, index=True)
    customer_id = fields.Many2one('field.sales.customer', string='Cliente', required=True)
    entered_at = fields.Datetime(string='Hora de llegada', required=True, default=fields.Datetime.now, index=True)
    distance_meters = fields.Float(string='Distancia (m)')
