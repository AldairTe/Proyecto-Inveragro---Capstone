# -*- coding: utf-8 -*-
from odoo import api, fields, models
from odoo.exceptions import UserError


class FieldSalesOrder(models.Model):
    _name = 'field.sales.order'
    _description = 'Pedido - App móvil'
    _order = 'date_order desc'
    _inherit = ['mail.thread']

    employee_id = fields.Many2one(
        'hr.employee', string='Vendedor', required=True,
        domain=[('is_mobile_seller', '=', True)], tracking=True,
    )
    customer_id = fields.Many2one('field.sales.customer', string='Cliente', required=True, tracking=True)
    date_order = fields.Datetime(string='Fecha', default=fields.Datetime.now, required=True)
    notes = fields.Text(string='Observaciones')
    signature = fields.Binary(string='Firma del cliente', attachment=True)

    line_ids = fields.One2many('field.sales.order.line', 'order_id', string='Productos')
    currency_id = fields.Many2one('res.currency', default=lambda self: self.env.company.currency_id)
    amount_total = fields.Monetary(compute='_compute_amount_total', string='Total', currency_field='currency_id', store=True)

    state = fields.Selection([
        ('new', 'Nuevo'),
        ('converted', 'Convertido'),
    ], default='new', string='Estado', tracking=True)
    sale_order_id = fields.Many2one('sale.order', string='Cotización/Venta generada', copy=False, readonly=True)

    external_uuid = fields.Char(
        string='UUID (app móvil)', copy=False, index=True,
        help='UUID generado por el celular al crear el pedido offline. '
             'Se usa para no duplicar el pedido cuando el celular sincroniza.',
    )

    _sql_constraints = [
        ('external_uuid_uniq', 'unique(external_uuid)', 'Este pedido ya fue sincronizado (UUID duplicado).'),
    ]

    @api.depends('line_ids.subtotal')
    def _compute_amount_total(self):
        for rec in self:
            rec.amount_total = sum(rec.line_ids.mapped('subtotal'))

    def _prepare_sale_order_vals(self):
        self.ensure_one()
        if not self.customer_id.partner_id:
            raise UserError(
                'El cliente "%s" no tiene un Contacto de Odoo vinculado. Ábrelo en '
                'Fuerza de Ventas > Clientes y asigna un "Contacto de Odoo (Ventas)" '
                'antes de convertir este pedido.' % self.customer_id.name
            )
        return {
            'partner_id': self.customer_id.partner_id.id,
            'date_order': self.date_order,
            'origin': 'App Móvil - Pedido #%s' % self.id,
            'order_line': [(0, 0, {
                'product_id': line.product_id.id,
                'product_uom_qty': line.qty,
                'price_unit': line.price_unit,
            }) for line in self.line_ids],
        }

    def action_convert_to_quotation(self):
        for rec in self:
            if rec.sale_order_id:
                raise UserError('Este pedido ya fue convertido.')
            sale_order = self.env['sale.order'].create(rec._prepare_sale_order_vals())
            rec.write({'sale_order_id': sale_order.id, 'state': 'converted'})
        return self.action_open_sale_order()

    def action_convert_to_sale(self):
        for rec in self:
            if rec.sale_order_id:
                raise UserError('Este pedido ya fue convertido.')
            sale_order = self.env['sale.order'].create(rec._prepare_sale_order_vals())
            sale_order.action_confirm()
            rec.write({'sale_order_id': sale_order.id, 'state': 'converted'})
        return self.action_open_sale_order()

    def action_open_sale_order(self):
        self.ensure_one()
        return {
            'type': 'ir.actions.act_window',
            'name': 'Cotización/Venta',
            'res_model': 'sale.order',
            'view_mode': 'form',
            'res_id': self.sale_order_id.id,
        }


class FieldSalesOrderLine(models.Model):
    _name = 'field.sales.order.line'
    _description = 'Línea de Pedido - App móvil'

    order_id = fields.Many2one('field.sales.order', required=True, ondelete='cascade')
    product_id = fields.Many2one('product.product', string='Producto', required=True)
    qty = fields.Float(string='Cantidad', default=1.0, required=True)
    price_unit = fields.Float(string='Precio unitario', required=True)
    subtotal = fields.Float(compute='_compute_subtotal', string='Subtotal', store=True)

    @api.depends('qty', 'price_unit')
    def _compute_subtotal(self):
        for rec in self:
            rec.subtotal = rec.qty * rec.price_unit
