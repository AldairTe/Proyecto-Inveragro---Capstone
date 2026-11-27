# -*- coding: utf-8 -*-
from odoo import api, fields, models


class FieldSalesSignatureConfig(models.Model):
    _name = 'field.sales.signature.config'
    _description = 'Configuración de Firma Digital - App móvil'

    name = fields.Char(string='Nombre', default='Configuración de Firma Digital')
    active_feature = fields.Boolean(
        string='Habilitar firma digital', default=False,
        help='Si está activo, la app ofrece capturar la firma del cliente al cerrar una '
             'visita o un pedido. Es opcional para el vendedor incluso si está habilitado.',
    )

    @api.model
    def get_active_config(self):
        config = self.search([], limit=1)
        if not config:
            config = self.create({})
        return config
