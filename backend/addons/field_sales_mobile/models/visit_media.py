# -*- coding: utf-8 -*-
from odoo import fields, models


class FieldSalesVisitMedia(models.Model):
    _name = 'field.sales.visit.media'
    _description = 'Evidencia multimedia de visita'
    _order = 'create_date desc'

    visit_id = fields.Many2one('field.sales.visit', string='Visita', required=True, ondelete='cascade')
    name = fields.Char(string='Nombre archivo', required=True)
    media_type = fields.Selection([
        ('image', 'Foto'),
        ('video', 'Video'),
    ], string='Tipo', required=True, default='image')
    file = fields.Binary(string='Archivo', attachment=True, required=True)
    mimetype = fields.Char(string='Tipo MIME')
    latitude = fields.Float(string='Latitud captura', digits=(10, 7))
    longitude = fields.Float(string='Longitud captura', digits=(10, 7))
    captured_at = fields.Datetime(string='Capturado el', default=fields.Datetime.now)
