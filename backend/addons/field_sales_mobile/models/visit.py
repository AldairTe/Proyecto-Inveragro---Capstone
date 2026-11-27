# -*- coding: utf-8 -*-
from odoo import fields, models


class FieldSalesVisit(models.Model):
    _name = 'field.sales.visit'
    _description = 'Visita de vendedor'
    _order = 'date_visit desc'
    _inherit = ['mail.thread']

    employee_id = fields.Many2one(
        'hr.employee', string='Vendedor', required=True,
        domain=[('is_mobile_seller', '=', True)], tracking=True
    )
    customer_id = fields.Many2one('field.sales.customer', string='Cliente', required=True, tracking=True)
    date_visit = fields.Datetime(string='Fecha y hora', default=fields.Datetime.now, required=True)
    latitude = fields.Float(string='Latitud', digits=(10, 7), required=True)
    longitude = fields.Float(string='Longitud', digits=(10, 7), required=True)
    notes = fields.Text(string='Observaciones')
    state = fields.Selection([
        ('pending', 'Pendiente'),
        ('completed', 'Realizada'),
    ], default='completed', string='Estado', tracking=True)
    media_ids = fields.One2many('field.sales.visit.media', 'visit_id', string='Evidencias')
    media_count = fields.Integer(compute='_compute_media_count')
    checklist_ids = fields.One2many(
        'field.sales.visit.checklist.answer', 'visit_id', string='Checklist agronómico'
    )
    signature = fields.Binary(string='Firma del cliente', attachment=True)

    external_uuid = fields.Char(
        string='UUID (app móvil)', copy=False, index=True,
        help='UUID generado por el celular al crear la visita en modo offline. '
             'Se usa para no duplicar la visita cuando el celular sincroniza.'
    )

    _sql_constraints = [
        ('external_uuid_uniq', 'unique(external_uuid)', 'Esta visita ya fue sincronizada (UUID duplicado).'),
    ]

    def _compute_media_count(self):
        for rec in self:
            rec.media_count = len(rec.media_ids)
