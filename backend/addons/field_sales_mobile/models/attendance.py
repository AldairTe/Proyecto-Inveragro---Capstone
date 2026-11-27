# -*- coding: utf-8 -*-
from odoo import api, fields, models


class FieldSalesAttendanceConfig(models.Model):
    _name = 'field.sales.attendance.config'
    _description = 'Configuración de Asistencia - App móvil'

    name = fields.Char(string='Nombre', default='Configuración de Asistencia')
    active_feature = fields.Boolean(
        string='Habilitar asistencia', default=True,
        help='Si está desactivado, la app no mostrará el botón de marcar asistencia '
             '(útil para empresas que no manejan control de asistencia por GPS).',
    )
    max_per_day = fields.Integer(
        string='Marcaciones permitidas por día', default=1,
        help='0 = sin límite. Algunas empresas solo permiten 1 marcación diaria, '
             'otras varias (entrada/salida, por fundo visitado, etc.).',
    )

    @api.model
    def get_active_config(self):
        """Devuelve (o crea) el registro único de configuración de asistencia."""
        config = self.search([], limit=1)
        if not config:
            config = self.create({})
        return config


class FieldSalesAttendance(models.Model):
    _name = 'field.sales.attendance'
    _description = 'Asistencia de vendedor'
    _order = 'date_attendance desc'
    _inherit = ['mail.thread']

    employee_id = fields.Many2one(
        'hr.employee', string='Vendedor', required=True, index=True,
        domain=[('is_mobile_seller', '=', True)], tracking=True,
    )
    date_attendance = fields.Datetime(string='Fecha y hora', default=fields.Datetime.now, required=True)
    latitude = fields.Float(string='Latitud', digits=(10, 7))
    longitude = fields.Float(string='Longitud', digits=(10, 7))

    external_uuid = fields.Char(
        string='UUID (app móvil)', copy=False, index=True,
        help='UUID generado por el celular al marcar en modo offline. '
             'Se usa para no duplicar la marcación cuando el celular sincroniza.',
    )

    _sql_constraints = [
        ('external_uuid_uniq', 'unique(external_uuid)', 'Esta asistencia ya fue sincronizada (UUID duplicado).'),
    ]
