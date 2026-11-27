# -*- coding: utf-8 -*-
from odoo import api, fields, models


class FieldSalesCustomer(models.Model):
    _name = 'field.sales.customer'
    _description = 'Cliente / Fundo'
    _order = 'name'
    _inherit = ['mail.thread']

    name = fields.Char(string='Nombre del fundo', required=True, tracking=True)
    contact_name = fields.Char(string='Nombre de contacto')
    phone = fields.Char(string='Teléfono')
    latitude = fields.Float(string='Latitud', digits=(10, 7), required=True)
    longitude = fields.Float(string='Longitud', digits=(10, 7), required=True)
    employee_id = fields.Many2one(
        'hr.employee', string='Vendedor asignado',
        domain=[('is_mobile_seller', '=', True)], tracking=True
    )
    partner_id = fields.Many2one(
        'res.partner', string='Contacto de Odoo (Ventas)', tracking=True,
        help='Vincula este fundo con un contacto de Odoo. Se requiere para poder convertir '
             'los pedidos hechos desde la app en cotizaciones o ventas.',
    )
    visit_ids = fields.One2many('field.sales.visit', 'customer_id', string='Visitas')
    visit_count = fields.Integer(compute='_compute_visit_count')
    active = fields.Boolean(default=True)
    external_uuid = fields.Char(
        string='UUID externo', copy=False, index=True,
        help='Identificador usado para migrar/conciliar datos con el sistema anterior.'
    )

    last_visit_date = fields.Datetime(
        string='Última visita', compute='_compute_last_visit', store=True,
        help='Fecha de la visita más reciente registrada para este cliente. '
             'Se recalcula cada vez que se crea una visita nueva.',
    )

    def _compute_visit_count(self):
        for rec in self:
            rec.visit_count = len(rec.visit_ids)

    @api.depends('visit_ids.date_visit')
    def _compute_last_visit(self):
        for rec in self:
            visits = rec.visit_ids.filtered('date_visit').sorted('date_visit', reverse=True)
            rec.last_visit_date = visits[0].date_visit if visits else False
