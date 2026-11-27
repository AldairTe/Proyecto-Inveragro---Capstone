# -*- coding: utf-8 -*-
from werkzeug.security import generate_password_hash

from odoo import api, fields, models
from odoo.exceptions import UserError


class FieldSalesSellerWizard(models.TransientModel):
    _name = 'field.sales.seller.wizard'
    _description = 'Registrar vendedor móvil'

    employee_id = fields.Many2one(
        'hr.employee', string='Empleado', required=True,
        domain=[('is_mobile_seller', '=', False)],
        help='Elige un empleado ya registrado para convertirlo en vendedor móvil.'
    )
    seller_code = fields.Char(string='Código de vendedor', required=True)
    sales_zone = fields.Char(string='Zona de venta')
    mobile_login = fields.Char(string='Usuario (app móvil)', required=True)
    mobile_password = fields.Char(string='Contraseña (app móvil)', required=True)

    @api.onchange('employee_id')
    def _onchange_employee_id(self):
        if self.employee_id and self.employee_id.work_email:
            self.mobile_login = self.employee_id.work_email

    def action_confirm(self):
        self.ensure_one()
        if self.env['hr.employee'].search_count([('mobile_login', '=', self.mobile_login)]):
            raise UserError('Ese usuario de app móvil ya está en uso.')
        if self.env['hr.employee'].search_count([('seller_code', '=', self.seller_code)]):
            raise UserError('Ese código de vendedor ya está en uso.')

        self.employee_id.write({
            'is_mobile_seller': True,
            'seller_code': self.seller_code,
            'sales_zone': self.sales_zone,
            'mobile_login': self.mobile_login,
            'mobile_password_hash': generate_password_hash(self.mobile_password),
            'mobile_account_active': True,
        })
        return {'type': 'ir.actions.act_window_close'}
