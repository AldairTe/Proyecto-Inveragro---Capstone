# -*- coding: utf-8 -*-
from werkzeug.security import generate_password_hash, check_password_hash

from odoo import api, fields, models


class HrEmployee(models.Model):
    _inherit = 'hr.employee'

    # ---- Marca de vendedor móvil ----
    is_mobile_seller = fields.Boolean(
        string='Es vendedor móvil',
        default=False,
        help='Marca a este empleado como vendedor de campo con acceso a la app móvil.'
    )
    seller_code = fields.Char(string='Código de vendedor', copy=False)
    sales_zone = fields.Char(string='Zona de venta')

    # ---- Credenciales para la app móvil (independientes de res.users) ----
    mobile_login = fields.Char(
        string='Usuario (app móvil)',
        copy=False,
        help='Usuario con el que el vendedor inicia sesión en la app móvil.'
    )
    mobile_password = fields.Char(
        string='Contraseña (app móvil)',
        store=False,
        help='Escribe una contraseña para crearla o cambiarla. No se muestra ni se guarda en texto plano.'
    )
    mobile_password_hash = fields.Char(
        string='Hash contraseña', copy=False, groups='base.group_system'
    )
    mobile_account_active = fields.Boolean(string='Cuenta móvil activa', default=True)

    # ---- Contadores para smart buttons ----
    customer_count = fields.Integer(compute='_compute_seller_counts', string='Clientes asignados')
    visit_count = fields.Integer(compute='_compute_seller_counts', string='Visitas')

    _sql_constraints = [
        ('seller_code_uniq', 'unique(seller_code)', 'Ese código de vendedor ya está en uso.'),
        ('mobile_login_uniq', 'unique(mobile_login)', 'Ese usuario de app móvil ya está en uso.'),
    ]

    def _compute_seller_counts(self):
        Customer = self.env['field.sales.customer']
        Visit = self.env['field.sales.visit']
        for emp in self:
            emp.customer_count = Customer.search_count([('employee_id', '=', emp.id)])
            emp.visit_count = Visit.search_count([('employee_id', '=', emp.id)])

    # ---- Hash de contraseña en create/write ----
    @staticmethod
    def _hash_mobile_password(vals):
        if vals.get('mobile_password'):
            vals['mobile_password_hash'] = generate_password_hash(vals['mobile_password'])
        vals.pop('mobile_password', None)
        return vals

    @api.model_create_multi
    def create(self, vals_list):
        vals_list = [self._hash_mobile_password(dict(v)) for v in vals_list]
        return super().create(vals_list)

    def write(self, vals):
        vals = self._hash_mobile_password(dict(vals))
        return super().write(vals)

    # ---- Autenticación usada por el controlador de la API ----
    def _check_mobile_credentials(self, login, password):
        """Devuelve el hr.employee si login/password son válidos, si no False."""
        employee = self.sudo().search([
            ('mobile_login', '=', login),
            ('is_mobile_seller', '=', True),
            ('mobile_account_active', '=', True),
        ], limit=1)
        if employee and employee.mobile_password_hash and \
                check_password_hash(employee.mobile_password_hash, password):
            return employee
        return False

    # ---- Navegación desde los smart buttons ----
    def action_view_customers(self):
        self.ensure_one()
        action = self.env.ref('field_sales_mobile.action_field_sales_customer').read()[0]
        action['domain'] = [('employee_id', '=', self.id)]
        action['context'] = {'default_employee_id': self.id}
        return action

    def action_view_visits(self):
        self.ensure_one()
        action = self.env.ref('field_sales_mobile.action_field_sales_visit').read()[0]
        action['domain'] = [('employee_id', '=', self.id)]
        action['context'] = {'default_employee_id': self.id}
        return action
