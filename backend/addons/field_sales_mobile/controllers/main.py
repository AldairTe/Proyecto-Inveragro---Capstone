# -*- coding: utf-8 -*-
import json
import logging

from odoo import fields, http
from odoo.http import request, Response
from odoo.exceptions import ValidationError, UserError
import base64

_logger = logging.getLogger(__name__)


class FieldSalesAPIController(http.Controller):
   

    # ---------------- helpers ----------------

    def _json_response(self, data, status=200):
        return Response(
            json.dumps(data, default=str),
            status=status,
            content_type='application/json',
        )

    def _authenticate(self):
        """Valida el header Authorization: Bearer <token>.

        Devuelve el hr.employee dueño del token, o False si no es válido.
        """
        auth_header = request.httprequest.headers.get('Authorization', '')
        token = None
        if auth_header.startswith('Bearer '):
            token = auth_header[len('Bearer '):].strip()
        if not token:
            return False
        return request.env['field.sales.auth.token'].sudo()._get_employee_from_token(token)

    # ---------------- activación (primer uso de la app) ----------------

    @http.route('/field_sales/api/activate', type='json', auth='none', methods=['POST'], csrf=False)
    def activate_device(self, **kwargs):
        params = kwargs.get('params', kwargs)
        code = params.get('activation_code')
        device_id = params.get('device_id')
        device_name = params.get('device_name')

        if not code or not device_id:
            return {'valid': False, 'error': 'activation_code y device_id son requeridos'}

        return request.env['field.sales.activation'].sudo()._validate_and_register_device(
            code, device_id, device_name
        )

    # ---------------- marca / white-label ----------------
    # Público (no requiere token): se consulta desde el login, antes de autenticarse.

    @http.route('/field_sales/api/branding', type='json', auth='none', methods=['POST', 'GET'], csrf=False)
    def get_branding(self, **kwargs):
        config = request.env['field.sales.branding.config'].sudo().get_active_config()
        return {
            'app_display_name': config.app_display_name,
            'primary_color': config.primary_color,
            'logo_b64': config.logo.decode('utf-8') if config.logo else None,
        }

    # ---------------- auth ----------------

    @http.route('/field_sales/api/login', type='json', auth='none', methods=['POST'], csrf=False)
    def login(self, **kwargs):
       
        login = kwargs.get('login')
        password = kwargs.get('password')
        if not login or not password:
            return {'error': 'login y password son requeridos'}

        employee = request.env['hr.employee'].sudo()._check_mobile_credentials(login, password)
        if not employee:
            return {'error': 'Credenciales inválidas'}

        token_rec = request.env['field.sales.auth.token'].sudo()._generate_for_employee(employee)
        return {
            'token': token_rec.token,
            'expires_at': token_rec.expires_at,
            'employee': {
                'id': employee.id,
                'name': employee.name,
                'seller_code': employee.seller_code,
                'sales_zone': employee.sales_zone,
            },  
        }

    # ---------------- clientes ----------------

    @http.route('/field_sales/api/customers', type='json', auth='none', methods=['POST', 'GET'], csrf=False)
    def get_customers(self, **kwargs):
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        customers = request.env['field.sales.customer'].sudo().search([
            ('employee_id', '=', employee.id),
            ('active', '=', True),
        ])
        data = [{
            'id': c.id,
            'name': c.name,
            'contact_name': c.contact_name,
            'phone': c.phone,
            'latitude': c.latitude,
            'longitude': c.longitude,
            'external_uuid': c.external_uuid,
        } for c in customers]
        return {'customers': data}

    # ---------------- visitas ----------------

    @http.route('/field_sales/api/visits', type='json', auth='none', methods=['POST', 'GET'], csrf=False)
    def get_visits(self, **kwargs):
        # Odoo con type='json' a veces desempaqueta 'params' o los deja en kwargs
        params = kwargs.get('params', kwargs)
        
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        # Construimos el dominio base para el vendedor logueado
        domain = [('employee_id', '=', employee.id)]
        
        # Si la app envía un customer_id específico, lo filtramos en el servidor
        customer_id = params.get('customer_id')
        if customer_id:
            domain.append(('customer_id', '=', int(customer_id)))

        visits = request.env['field.sales.visit'].sudo().search(
            domain, order='date_visit desc', limit=200
        )
        
        data = [{
            'id': v.id,
            'external_uuid': v.external_uuid,
            'customer_id': v.customer_id.id if v.customer_id else 0,
            'customer_name': v.customer_id.name if v.customer_id else '',
            'date_visit': v.date_visit,
            'latitude': v.latitude,
            'longitude': v.longitude,
            'notes': v.notes,
            'state': v.state,
        } for v in visits]
        
        return {'visits': data}
    

    @http.route('/field_sales/api/visits/create', type='json', auth='none', methods=['POST'], csrf=False)
    def create_visit(self, **kwargs):
        # Odoo con type='json' a veces desempaqueta 'params' automáticamente, 
        # pero a veces deja todo dentro de kwargs['params']. Asegurémonos:
        params = kwargs.get('params', kwargs)
        
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        external_uuid = params.get('external_uuid')
        if not external_uuid:
            return {'error': 'external_uuid es requerido'}

        Visit = request.env['field.sales.visit'].sudo()
        
        existing = Visit.search([('external_uuid', '=', external_uuid)], limit=1)
        if existing:
            return {
                'id': existing.id, 'external_uuid': existing.external_uuid, 'duplicated': True,
            }

        customer = request.env['field.sales.customer'].sudo().browse(params.get('customer_id'))
        if not customer.exists():
            return {'error': 'Cliente no encontrado en Odoo'}

        try:
            checklist_vals = []
            for answer in (params.get('checklist') or []):
                item_id = answer.get('item_id')
                if not item_id:
                    continue
                checklist_vals.append((0, 0, {
                    'item_id': item_id,
                    'checked': bool(answer.get('checked')),
                }))

            visit_vals = {
                'employee_id': employee.id,
                'customer_id': customer.id,
                'external_uuid': external_uuid,
                'date_visit': params.get('date_visit') or fields.Datetime.now(),
                'latitude': params.get('latitude', 0.0),
                'longitude': params.get('longitude', 0.0),
                'notes': params.get('notes'),
                'state': params.get('state', 'completed'),
                'checklist_ids': checklist_vals,
            }
            if params.get('signature_b64'):
                visit_vals['signature'] = params.get('signature_b64')

            visit = Visit.with_context(tracking_disable=True).create(visit_vals)
            return {'id': visit.id, 'external_uuid': visit.external_uuid}

        except Exception as e:
            import traceback
            error_trace = traceback.format_exc()
            _logger.error(error_trace)
            return {'error': str(e)}

    @http.route('/field_sales/api/orders', type='json', auth='none', methods=['POST', 'GET'], csrf=False)
    def get_orders(self, **kwargs):
        params = kwargs.get('params', kwargs)
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        domain = [('employee_id', '=', employee.id)]
        customer_id = params.get('customer_id')
        if customer_id:
            domain.append(('customer_id', '=', int(customer_id)))

        orders = request.env['field.sales.order'].sudo().search(domain, order='date_order desc', limit=50)
        data = [{
            'id': o.id,
            'customer_id': o.customer_id.id,
            'date_order': o.date_order,
            'amount_total': o.amount_total,
            'state': o.state,
        } for o in orders]
        return {'orders': data}

    # ---------------- checklist agronómico ----------------

    @http.route('/field_sales/api/checklist/items', type='json', auth='none', methods=['POST', 'GET'], csrf=False)
    def get_checklist_items(self, **kwargs):
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        items = request.env['field.sales.checklist.item'].sudo().search([('active', '=', True)])
        data = [{'id': item.id, 'name': item.name} for item in items]
        return {'items': data}

    # ---------------- firma digital ----------------

    @http.route('/field_sales/api/signature/config', type='json', auth='none', methods=['POST', 'GET'], csrf=False)
    def get_signature_config(self, **kwargs):
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        config = request.env['field.sales.signature.config'].sudo().get_active_config()
        return {'enabled': config.active_feature}
    

    
    # ---------------- asistencia (configurable por empresa) ----------------

    @http.route('/field_sales/api/attendance/config', type='json', auth='none', methods=['POST', 'GET'], csrf=False)
    def get_attendance_config(self, **kwargs):
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        Attendance = request.env['field.sales.attendance'].sudo()
        config = request.env['field.sales.attendance.config'].sudo().get_active_config()

        today = fields.Date.context_today(Attendance)
        marks_today = Attendance.search_count([
            ('employee_id', '=', employee.id),
            ('date_attendance', '>=', f'{today} 00:00:00'),
            ('date_attendance', '<=', f'{today} 23:59:59'),
        ])

        return {
            'enabled': config.active_feature,
            'max_per_day': config.max_per_day,
            'marks_today': marks_today,
        }

    @http.route('/field_sales/api/attendance/create', type='json', auth='none', methods=['POST'], csrf=False)
    def create_attendance(self, **kwargs):
        params = kwargs.get('params', kwargs)
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        external_uuid = params.get('external_uuid')
        if not external_uuid:
            return {'error': 'external_uuid es requerido'}

        Attendance = request.env['field.sales.attendance'].sudo()

        existing = Attendance.search([('external_uuid', '=', external_uuid)], limit=1)
        if existing:
            return {'id': existing.id, 'external_uuid': existing.external_uuid, 'duplicated': True}

        config = request.env['field.sales.attendance.config'].sudo().get_active_config()
        if not config.active_feature:
            return {'error': 'La asistencia está desactivada para esta empresa'}

        if config.max_per_day:
            today = fields.Date.context_today(Attendance)
            marks_today = Attendance.search_count([
                ('employee_id', '=', employee.id),
                ('date_attendance', '>=', f'{today} 00:00:00'),
                ('date_attendance', '<=', f'{today} 23:59:59'),
            ])
            if marks_today >= config.max_per_day:
                return {'error': 'Ya alcanzaste el máximo de marcaciones permitidas hoy'}

        try:
            attendance = Attendance.with_context(tracking_disable=True).create({
                'employee_id': employee.id,
                'external_uuid': external_uuid,
                'date_attendance': params.get('date_attendance') or fields.Datetime.now(),
                'latitude': params.get('latitude', 0.0),
                'longitude': params.get('longitude', 0.0),
            })
            return {'id': attendance.id, 'external_uuid': attendance.external_uuid}
        except Exception as e:
            import traceback
            _logger.error(traceback.format_exc())
            return {'error': str(e)}

    # ---------------- ubicación en vivo (configurable por empresa) ----------------

    @http.route('/field_sales/api/location/config', type='json', auth='none', methods=['POST', 'GET'], csrf=False)
    def get_location_config(self, **kwargs):
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        config = request.env['field.sales.location.config'].sudo().get_active_config()
        return {
            'enabled': config.active_feature,
            'interval_seconds': config.ping_interval_seconds,
            'geofence_radius_meters': config.geofence_radius_meters,
        }

    @http.route('/field_sales/api/location/ping', type='json', auth='none', methods=['POST'], csrf=False)
    def create_location_ping(self, **kwargs):
        params = kwargs.get('params', kwargs)
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        config = request.env['field.sales.location.config'].sudo().get_active_config()
        if not config.active_feature:
            return {'error': 'Ubicación en vivo desactivada para esta empresa'}

        try:
            ping = request.env['field.sales.location.ping'].sudo().create({
                'employee_id': employee.id,
                'latitude': params.get('latitude', 0.0),
                'longitude': params.get('longitude', 0.0),
                'accuracy': params.get('accuracy', 0.0),
                'battery_level': params.get('battery_level'),
                'captured_at': params.get('captured_at') or fields.Datetime.now(),
            })
            request.env['field.sales.location.ping']._detectar_geocerca(employee, ping)
            return {'ok': True}
        except Exception as e:
            import traceback
            _logger.error(traceback.format_exc())
            return {'error': str(e)}

    @http.route('/field_sales/api/location/my_route', type='json', auth='none', methods=['POST', 'GET'], csrf=False)
    def get_my_route(self, **kwargs):
        """Transparencia: el propio vendedor puede ver su ruta registrada hoy."""
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        today = fields.Date.context_today(request.env['field.sales.location.ping'])
        pings = request.env['field.sales.location.ping'].sudo().search([
            ('employee_id', '=', employee.id),
            ('captured_at', '>=', '%s 00:00:00' % today),
            ('captured_at', '<=', '%s 23:59:59' % today),
        ], order='captured_at asc')

        data = [{
            'latitude': p.latitude,
            'longitude': p.longitude,
            'captured_at': p.captured_at,
        } for p in pings]
        return {'points': data}

    # ---------------- ubicación en vivo: endpoints internos para el mapa del backend ----------------

    def _location_status(self, ultimo_ping, config):
        if not ultimo_ping:
            return 'sin_datos'
        minutos = (fields.Datetime.now() - ultimo_ping.captured_at).total_seconds() / 60.0
        umbral = config.offline_threshold_minutes or 10
        if minutos <= umbral / 2.0:
            return 'online'
        elif minutos <= umbral:
            return 'alerta'
        return 'offline'

    @http.route('/field_sales/backend/location/latest', type='json', auth='user', methods=['POST'], csrf=False)
    def backend_location_latest(self, **kwargs):
        if not request.env.user.has_group('field_sales_mobile.group_field_sales_user'):
            return {'error': 'No autorizado'}

        Ping = request.env['field.sales.location.ping'].sudo()
        config = request.env['field.sales.location.config'].sudo().get_active_config()
        empleados = request.env['hr.employee'].sudo().search([
            ('is_mobile_seller', '=', True), ('mobile_account_active', '=', True),
        ])

        data = []
        for emp in empleados:
            ultimo = Ping.search([('employee_id', '=', emp.id)], order='captured_at desc', limit=1)
            if not ultimo:
                continue
            data.append({
                'employee_id': emp.id,
                'employee_name': emp.name,
                'latitude': ultimo.latitude,
                'longitude': ultimo.longitude,
                'captured_at': ultimo.captured_at,
                'battery_level': ultimo.battery_level,
                'status': self._location_status(ultimo, config),
                'low_battery': ultimo.battery_level is not False and ultimo.battery_level <= (config.low_battery_threshold or 20),
            })
        return {'positions': data}

    @http.route('/field_sales/backend/location/route', type='json', auth='user', methods=['POST'], csrf=False)
    def backend_location_route(self, **kwargs):
        if not request.env.user.has_group('field_sales_mobile.group_field_sales_user'):
            return {'error': 'No autorizado'}

        params = kwargs.get('params', kwargs)
        employee_id = params.get('employee_id')
        fecha = params.get('date')
        if not employee_id or not fecha:
            return {'error': 'employee_id y date son requeridos'}

        pings = request.env['field.sales.location.ping'].sudo().search([
            ('employee_id', '=', int(employee_id)),
            ('captured_at', '>=', '%s 00:00:00' % fecha),
            ('captured_at', '<=', '%s 23:59:59' % fecha),
        ], order='captured_at asc')

        data = [{
            'latitude': p.latitude,
            'longitude': p.longitude,
            'captured_at': p.captured_at,
            'battery_level': p.battery_level,
        } for p in pings]

        # Resumen del día: km recorridos (Haversine acumulado) y ventana horaria.
        km = 0.0
        Ping = request.env['field.sales.location.ping']
        for i in range(1, len(pings)):
            km += Ping._distancia_metros(
                pings[i - 1].latitude, pings[i - 1].longitude,
                pings[i].latitude, pings[i].longitude,
            ) / 1000.0

        visitas_dia = request.env['field.sales.visit'].sudo().search_count([
            ('employee_id', '=', int(employee_id)),
            ('date_visit', '>=', '%s 00:00:00' % fecha),
            ('date_visit', '<=', '%s 23:59:59' % fecha),
        ])

        resumen = {
            'km_recorridos': round(km, 2),
            'total_puntos': len(pings),
            'primera_hora': pings[0].captured_at if pings else None,
            'ultima_hora': pings[-1].captured_at if pings else None,
            'visitas_del_dia': visitas_dia,
        }
        return {'points': data, 'summary': resumen}

    @http.route('/field_sales/backend/location/planned_stops', type='json', auth='user', methods=['POST'], csrf=False)
    def backend_location_planned_stops(self, **kwargs):
        if not request.env.user.has_group('field_sales_mobile.group_field_sales_user'):
            return {'error': 'No autorizado'}

        params = kwargs.get('params', kwargs)
        employee_id = params.get('employee_id')
        fecha = params.get('date')
        if not employee_id or not fecha:
            return {'error': 'employee_id y date son requeridos'}

        paradas = request.env['field.sales.route.stop'].sudo().search([
            ('employee_id', '=', int(employee_id)),
            ('planned_date', '=', fecha),
        ], order='sequence, id')

        data = [{
            'customer_name': p.customer_id.name,
            'latitude': p.customer_id.latitude,
            'longitude': p.customer_id.longitude,
            'sequence': p.sequence,
            'done': p.done,
        } for p in paradas if p.customer_id.latitude or p.customer_id.longitude]
        return {'stops': data}

    @http.route('/field_sales/backend/location/heatmap', type='json', auth='user', methods=['POST'], csrf=False)
    def backend_location_heatmap(self, **kwargs):
        if not request.env.user.has_group('field_sales_mobile.group_field_sales_user'):
            return {'error': 'No autorizado'}

        params = kwargs.get('params', kwargs)
        dias = int(params.get('days', 30))
        limite = fields.Datetime.now() - __import__('datetime').timedelta(days=dias)

        pings = request.env['field.sales.location.ping'].sudo().search(
            [('captured_at', '>=', limite)], limit=5000,
        )
        return {'points': [[p.latitude, p.longitude] for p in pings]}

    @http.route('/field_sales/backend/location/events', type='json', auth='user', methods=['POST'], csrf=False)
    def backend_location_events(self, **kwargs):
        if not request.env.user.has_group('field_sales_mobile.group_field_sales_user'):
            return {'error': 'No autorizado'}

        eventos = request.env['field.sales.geofence.event'].sudo().search([], order='entered_at desc', limit=20)
        return {'events': [{
            'employee_name': e.employee_id.name,
            'customer_name': e.customer_id.name,
            'entered_at': e.entered_at,
        } for e in eventos]}

    @http.route('/field_sales/backend/location/employees', type='json', auth='user', methods=['POST'], csrf=False)
    def backend_location_employees(self, **kwargs):
        if not request.env.user.has_group('field_sales_mobile.group_field_sales_user'):
            return {'error': 'No autorizado'}
        empleados = request.env['hr.employee'].sudo().search([('is_mobile_seller', '=', True)])
        return {'employees': [{'id': e.id, 'name': e.name} for e in empleados]}

    @http.route('/field_sales/backend/location/export.csv', type='http', auth='user', methods=['GET'], csrf=False)
    def backend_location_export_csv(self, employee_id=None, date=None, **kwargs):
        if not request.env.user.has_group('field_sales_mobile.group_field_sales_user'):
            return request.not_found()
        if not employee_id or not date:
            return request.not_found()

        pings = request.env['field.sales.location.ping'].sudo().search([
            ('employee_id', '=', int(employee_id)),
            ('captured_at', '>=', '%s 00:00:00' % date),
            ('captured_at', '<=', '%s 23:59:59' % date),
        ], order='captured_at asc')

        lineas = ['fecha_hora,latitud,longitud,precision_m,bateria_pct']
        for p in pings:
            lineas.append('%s,%s,%s,%s,%s' % (
                p.captured_at, p.latitude, p.longitude, p.accuracy, p.battery_level or ''
            ))
        contenido = '\n'.join(lineas)

        return Response(
            contenido,
            headers=[
                ('Content-Type', 'text/csv'),
                ('Content-Disposition', 'attachment; filename="ruta_%s_%s.csv"' % (employee_id, date)),
            ],
        )

    @http.route('/field_sales/backend/location/export.kml', type='http', auth='user', methods=['GET'], csrf=False)
    def backend_location_export_kml(self, employee_id=None, date=None, **kwargs):
        if not request.env.user.has_group('field_sales_mobile.group_field_sales_user'):
            return request.not_found()
        if not employee_id or not date:
            return request.not_found()

        employee = request.env['hr.employee'].sudo().browse(int(employee_id))
        pings = request.env['field.sales.location.ping'].sudo().search([
            ('employee_id', '=', int(employee_id)),
            ('captured_at', '>=', '%s 00:00:00' % date),
            ('captured_at', '<=', '%s 23:59:59' % date),
        ], order='captured_at asc')

        coords = ' '.join(['%s,%s,0' % (p.longitude, p.latitude) for p in pings])
        kml = """<?xml version="1.0" encoding="UTF-8"?>
<kml xmlns="http://www.opengis.net/kml/2.2">
<Document>
<name>Ruta %s - %s</name>
<Placemark>
<name>Ruta del día</name>
<LineString>
<tessellate>1</tessellate>
<coordinates>%s</coordinates>
</LineString>
</Placemark>
</Document>
</kml>""" % (employee.name, date, coords)

        return Response(
            kml,
            headers=[
                ('Content-Type', 'application/vnd.google-earth.kml+xml'),
                ('Content-Disposition', 'attachment; filename="ruta_%s_%s.kml"' % (employee_id, date)),
            ],
        )

    @http.route('/field_sales/live_map', type='http', auth='user', methods=['GET'], csrf=False)
    def live_map_page(self, **kwargs):
        if not request.env.user.has_group('field_sales_mobile.group_field_sales_user'):
            return request.not_found()
        return request.render('field_sales_mobile.live_map_template', {})

    # ---------------- planificación de rutas ----------------

    @http.route('/field_sales/api/route/today', type='json', auth='none', methods=['POST', 'GET'], csrf=False)
    def get_route_today(self, **kwargs):
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        config = request.env['field.sales.route.config'].sudo().get_active_config()
        if not config.active_feature:
            return {'enabled': False, 'stops': []}

        today = fields.Date.context_today(request.env['field.sales.route.stop'])
        stops = request.env['field.sales.route.stop'].sudo().search([
            ('employee_id', '=', employee.id),
            ('planned_date', '=', today),
        ], order='sequence, id')

        data = [{
            'customer_id': s.customer_id.id,
            'customer_name': s.customer_id.name,
            'sequence': s.sequence,
            'done': s.done,
            'notes': s.notes or '',
        } for s in stops]

        return {'enabled': True, 'stops': data}

    # ---------------- catálogo de productos (configurable por empresa) ----------------

    @http.route('/field_sales/api/catalog', type='json', auth='none', methods=['POST', 'GET'], csrf=False)
    def get_catalog(self, **kwargs):
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        config = request.env['field.sales.catalog.config'].sudo().get_active_config()

        domain = [('sale_ok', '=', True), ('active', '=', True)]
        if config.product_category_id:
            domain.append(('categ_id', 'child_of', config.product_category_id.id))

        products = request.env['product.product'].sudo().search(domain, limit=500)

        pricelist = config.pricelist_id
        if not pricelist:
            pricelist = request.env['product.pricelist'].sudo().search([], limit=1)

        partner = employee.user_id.partner_id if employee.user_id else False

        # Las peticiones vía auth='none' no arrastran una compañía en el contexto (env.company
        # falla con AssertionError al convertir moneda dentro de get_product_price). La forzamos
        # explícitamente a partir de la compañía del vendedor.
        company = employee.company_id or request.env.user.company_id
        if pricelist:
            pricelist = pricelist.with_context(force_company=company.id, allowed_company_ids=[company.id])

        data = []
        for product in products:
            if pricelist:
                price = pricelist.get_product_price(product, 1.0, partner)
            else:
                price = product.lst_price
            product_data = {
                'id': product.id,
                'name': product.display_name,
                'default_code': product.default_code or '',
                'uom_name': product.uom_id.name,
                'price': price,
            }
            if config.show_stock:
                product_data['qty_available'] = product.qty_available
            data.append(product_data)

        return {'products': data}

    # ---------------- pedidos ----------------
    # Los pedidos creados desde la app quedan en nuestro propio modelo (field.sales.order),
    # no tocan directamente sale.order: el equipo de ventas los revisa en Odoo y decide
    # convertirlos a Cotización o Venta manualmente (ver order.py / order_views.xml).

    @http.route('/field_sales/api/orders/create', type='json', auth='none', methods=['POST'], csrf=False)
    def create_order(self, **kwargs):
        params = kwargs.get('params', kwargs)
        employee = self._authenticate()
        if not employee:
            return {'error': 'No autorizado'}

        external_uuid = params.get('external_uuid')
        if not external_uuid:
            return {'error': 'external_uuid es requerido'}

        Order = request.env['field.sales.order'].sudo()

        existing = Order.search([('external_uuid', '=', external_uuid)], limit=1)
        if existing:
            return {'id': existing.id, 'external_uuid': existing.external_uuid, 'duplicated': True}

        customer = request.env['field.sales.customer'].sudo().browse(params.get('customer_id'))
        if not customer.exists():
            return {'error': 'Cliente no encontrado en Odoo'}

        lines = params.get('lines') or []
        if not lines:
            return {'error': 'El pedido debe tener al menos un producto'}

        line_vals = []
        for line in lines:
            product = request.env['product.product'].sudo().browse(line.get('product_id'))
            if not product.exists():
                continue
            line_vals.append((0, 0, {
                'product_id': product.id,
                'qty': line.get('qty', 1.0),
                'price_unit': line.get('price_unit', product.lst_price),
            }))

        if not line_vals:
            return {'error': 'Ningún producto del pedido es válido'}

        try:
            order_vals = {
                'employee_id': employee.id,
                'customer_id': customer.id,
                'external_uuid': external_uuid,
                'date_order': params.get('date_order') or fields.Datetime.now(),
                'notes': params.get('notes'),
                'line_ids': line_vals,
            }
            if params.get('signature_b64'):
                order_vals['signature'] = params.get('signature_b64')

            order = Order.with_context(tracking_disable=True).create(order_vals)
            return {'id': order.id, 'external_uuid': order.external_uuid}
        except Exception as e:
            import traceback
            _logger.error(traceback.format_exc())
            return {'error': str(e)}

    # ---------------- evidencias (fotos / videos) ----------------
    # NOTA: Se mantiene en type='http' porque procesa archivos binarios (multipart/form-data)

    @http.route('/field_sales/api/visits/<int:visit_id>/media', type='http', auth='none', methods=['POST'], csrf=False)
    def upload_media(self, visit_id, **kwargs):
        employee = self._authenticate()
        if not employee:
            return self._json_response({'error': 'No autorizado'}, status=401)

        visit = request.env['field.sales.visit'].sudo().browse(visit_id)
        if not visit.exists() or visit.employee_id.id != employee.id:
            return self._json_response({'error': 'Visita no encontrada'}, status=404)

        upload_file = request.httprequest.files.get('file')
        if not upload_file:
            return self._json_response({'error': 'El archivo físico (file) es requerido'}, status=400)

        form_data = request.httprequest.form

        try:
            file_content = upload_file.read()
            file_b64 = base64.b64encode(file_content)

            media = request.env['field.sales.visit.media'].sudo().with_context(tracking_disable=True).create({
                'visit_id': visit.id,
                'name': form_data.get('name', upload_file.filename or 'evidencia'),
                'media_type': form_data.get('media_type', 'image'),
                'file': file_b64,
                'mimetype': upload_file.mimetype,
                'latitude': form_data.get('latitude'),
                'longitude': form_data.get('longitude'),
            })
            return self._json_response({'id': media.id}, status=201)
        except Exception as e:
            import traceback
            _logger.error(traceback.format_exc())
            return self._json_response({'error': 'Error guardando archivo', 'details': str(e)}, status=500)