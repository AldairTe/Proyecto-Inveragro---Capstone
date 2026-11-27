# -*- coding: utf-8 -*-
import secrets
from datetime import timedelta

from odoo import api, fields, models


class FieldSalesAuthToken(models.Model):
    _name = 'field.sales.auth.token'
    _description = 'Token de sesión - App móvil'
    _order = 'create_date desc'

    employee_id = fields.Many2one(
        'hr.employee', string='Vendedor', required=True, ondelete='cascade', index=True
    )
    token = fields.Char(string='Token', required=True, index=True, copy=False)
    expires_at = fields.Datetime(string='Expira', required=True)
    active = fields.Boolean(default=True)

    _sql_constraints = [
        ('token_uniq', 'unique(token)', 'El token ya existe.'),
    ]

    @api.model
    def _generate_for_employee(self, employee, days_valid=30):
        """Invalida tokens anteriores y genera uno nuevo para el vendedor."""
        self.search([('employee_id', '=', employee.id), ('active', '=', True)]).write({'active': False})
        record = self.create({
            'employee_id': employee.id,
            'token': secrets.token_urlsafe(32),
            'expires_at': fields.Datetime.now() + timedelta(days=days_valid),
        })
        return record

    @api.model
    def _get_employee_from_token(self, token):
        if not token:
            return False
        rec = self.sudo().search([
            ('token', '=', token),
            ('active', '=', True),
            ('expires_at', '>', fields.Datetime.now()),
        ], limit=1)
        return rec.employee_id if rec else False
