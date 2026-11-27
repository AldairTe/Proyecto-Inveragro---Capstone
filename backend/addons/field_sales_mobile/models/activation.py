# -*- coding: utf-8 -*-
import base64
import secrets

from odoo import api, fields, models


class FieldSalesActivation(models.Model):
    _name = 'field.sales.activation'
    _description = 'Código de activación - App móvil'

    name = fields.Char(string='Nombre', default='Activación App Móvil')
    activation_code = fields.Char(
        string='Código de activación', required=True, copy=False, index=True,
        default=lambda self: self._generate_code(),
    )
    server_url = fields.Char(string='URL del servidor', compute='_compute_server_url')
    max_devices = fields.Integer(
        string='Máx. dispositivos', default=0,
        help='Cantidad de celulares que pueden activarse con este código. 0 = sin límite.',
    )
    device_ids = fields.One2many('field.sales.activation.device', 'activation_id', string='Dispositivos')
    device_count = fields.Integer(compute='_compute_device_count', string='Dispositivos activos')
    qr_image = fields.Binary(string='Código QR', compute='_compute_qr_image')

    _sql_constraints = [
        ('activation_code_uniq', 'unique(activation_code)', 'Ya existe un código de activación igual a este.'),
    ]

    @api.depends('device_ids.active')
    def _compute_device_count(self):
        for rec in self:
            rec.device_count = len(rec.device_ids.filtered('active'))

    def _compute_server_url(self):
        base_url = self.env['ir.config_parameter'].sudo().get_param('web.base.url', '')
        for rec in self:
            rec.server_url = base_url

    def _compute_qr_image(self):
        # Reutiliza el generador de códigos de barra/QR que ya trae Odoo (módulo report,
        # basado en reportlab) para no depender de librerías externas que el Odoo del
        # cliente podría no tener instaladas.
        for rec in self:
            rec.qr_image = False
            if not rec.activation_code:
                continue
            try:
                payload = '%s|%s' % (rec.server_url or '', rec.activation_code)
                png = self.env['ir.actions.report'].barcode('QR', payload, width=300, height=300)
                rec.qr_image = base64.b64encode(png)
            except Exception:
                rec.qr_image = False

    @api.model
    def _generate_code(self):
        return secrets.token_hex(4).upper()

    @api.model
    def get_active_config(self):
        """Devuelve (o crea) el registro de configuración de activación."""
        config = self.search([], limit=1)
        if not config:
            config = self.create({'activation_code': self._generate_code()})
        return config

    def action_regenerate_code(self):
        for rec in self:
            rec.activation_code = self._generate_code()
            rec.device_ids.write({'active': False})
        return True

    def action_open_devices(self):
        self.ensure_one()
        return {
            'type': 'ir.actions.act_window',
            'name': 'Dispositivos Activados',
            'res_model': 'field.sales.activation.device',
            'view_mode': 'tree,form',
            'domain': [('activation_id', '=', self.id)],
            'context': {'default_activation_id': self.id},
        }

    def _validate_and_register_device(self, code, device_id, device_name=None):
        """Valida un código de activación y registra/reactiva el dispositivo.

        Usado por el endpoint /field_sales/api/activate: evita que alguien ajeno
        configure la app apuntando a este servidor sin conocer el código generado
        aquí, y limita cuántos celulares pueden usarlo si así se configura.
        """
        config = self.sudo().search([('activation_code', '=', (code or '').strip())], limit=1)
        if not config:
            return {'valid': False, 'error': 'Código de activación inválido'}

        Device = self.env['field.sales.activation.device'].sudo()
        existing = Device.search([
            ('activation_id', '=', config.id), ('device_id', '=', device_id),
        ], limit=1)

        if existing:
            if not existing.active:
                existing.active = True
            if device_name:
                existing.device_name = device_name
            return {'valid': True, 'server_url': config.server_url}

        if config.max_devices and config.device_count >= config.max_devices:
            return {'valid': False, 'error': 'Se alcanzó el límite de dispositivos activados para este código. Contacta al administrador.'}

        Device.create({
            'activation_id': config.id,
            'device_id': device_id,
            'device_name': device_name,
        })
        return {'valid': True, 'server_url': config.server_url}


class FieldSalesActivationDevice(models.Model):
    _name = 'field.sales.activation.device'
    _description = 'Dispositivo activado - App móvil'
    _order = 'create_date desc'

    activation_id = fields.Many2one('field.sales.activation', string='Código de activación', required=True, ondelete='cascade')
    device_id = fields.Char(string='ID de dispositivo', required=True)
    device_name = fields.Char(string='Dispositivo')
    active = fields.Boolean(default=True)

    _sql_constraints = [
        ('device_uniq', 'unique(activation_id, device_id)', 'Este dispositivo ya está registrado con este código.'),
    ]
