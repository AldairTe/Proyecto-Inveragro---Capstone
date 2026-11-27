# -*- coding: utf-8 -*-
from odoo import api, fields, models


class FieldSalesDashboard(models.TransientModel):
    _name = 'field.sales.dashboard'
    _description = 'Dashboard Fuerza de Ventas'

    total_sellers = fields.Integer(string='Vendedores activos', readonly=True)
    total_customers = fields.Integer(string='Clientes asignados', readonly=True)
    visits_today = fields.Integer(string='Visitas hoy', readonly=True)
    pending_visits = fields.Integer(string='Visitas pendientes', readonly=True)
    media_count = fields.Integer(string='Evidencias cargadas', readonly=True)

    @api.model
    def default_get(self, fields_list):
        res = super().default_get(fields_list)

        Employee = self.env['hr.employee']
        Customer = self.env['field.sales.customer']
        Visit = self.env['field.sales.visit']
        Media = self.env['field.sales.visit.media']

        # Cálculo simple del día actual
        # respetar la zona horaria de cada usuario
        today = fields.Date.context_today(self)
        domain_today = [
            ('date_visit', '>=', f'{today} 00:00:00'),
            ('date_visit', '<=', f'{today} 23:59:59'),
        ]

        res.update({
            'total_sellers': Employee.search_count([
                ('is_mobile_seller', '=', True),
                ('mobile_account_active', '=', True),
            ]),
            'total_customers': Customer.search_count([('active', '=', True)]),
            'visits_today': Visit.search_count(domain_today),
            'pending_visits': Visit.search_count([('state', '=', 'pending')]),
            'media_count': Media.search_count([]),
        })
        return res

    def action_open_sellers(self):
        return self.env.ref('field_sales_mobile.action_field_sales_employees').read()[0]

    def action_open_customers(self):
        return self.env.ref('field_sales_mobile.action_field_sales_customer').read()[0]

    def action_open_visits(self):
        return self.env.ref('field_sales_mobile.action_field_sales_visit').read()[0]

    def action_open_reports(self):
        return self.env.ref('field_sales_mobile.action_field_sales_visit_report').read()[0]
