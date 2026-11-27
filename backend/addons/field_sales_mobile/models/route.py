# -*- coding: utf-8 -*-
from odoo import api, fields, models


class FieldSalesRouteConfig(models.Model):
    _name = 'field.sales.route.config'
    _description = 'Configuración de Planificación de Rutas - App móvil'

    name = fields.Char(string='Nombre', default='Configuración de Rutas')
    active_feature = fields.Boolean(
        string='Habilitar planificación de rutas', default=False,
        help='Si está activo, la app solo muestra a cada vendedor los clientes '
             'planificados para el día (en vez de la lista completa de asignados). '
             'Si no hay ninguna parada planificada para un vendedor en un día dado, '
             'la app cae de vuelta a mostrarle todos sus clientes asignados.',
    )

    @api.model
    def get_active_config(self):
        config = self.search([], limit=1)
        if not config:
            config = self.create({})
        return config


class FieldSalesRouteStop(models.Model):
    _name = 'field.sales.route.stop'
    _description = 'Parada de Ruta Planificada'
    _order = 'planned_date, sequence, id'

    employee_id = fields.Many2one(
        'hr.employee', string='Vendedor', required=True, index=True,
        domain=[('is_mobile_seller', '=', True)],
    )
    customer_id = fields.Many2one('field.sales.customer', string='Cliente', required=True)
    planned_date = fields.Date(string='Fecha planificada', required=True, default=fields.Date.context_today, index=True)
    sequence = fields.Integer(string='Orden', default=10)
    notes = fields.Char(string='Notas')
    done = fields.Boolean(string='Visitado', compute='_compute_done')

    _sql_constraints = [
        ('stop_uniq', 'unique(employee_id, customer_id, planned_date)',
         'Este cliente ya está planificado para ese vendedor en esa fecha.'),
    ]

    def _compute_done(self):
        Visit = self.env['field.sales.visit']
        for rec in self:
            rec.done = bool(Visit.search_count([
                ('employee_id', '=', rec.employee_id.id),
                ('customer_id', '=', rec.customer_id.id),
                ('date_visit', '>=', '%s 00:00:00' % rec.planned_date),
                ('date_visit', '<=', '%s 23:59:59' % rec.planned_date),
            ])) if rec.planned_date else False
