# -*- coding: utf-8 -*-
from odoo import api, fields, models


class FieldSalesBrandingConfig(models.Model):
    _name = 'field.sales.branding.config'
    _description = 'Configuración de Marca (White-Label) - App móvil'

    name = fields.Char(string='Nombre interno', default='Configuración de Marca')
    app_display_name = fields.Char(
        string='Nombre visible en la app', default='Fuerza de Ventas',
        help='Nombre de la empresa que se muestra en la app (login y dashboard).',
    )
    primary_color = fields.Char(
        string='Color primario (hex)', default='#252525',
        help='Color de acento usado en botones y encabezados de la app. Formato: #RRGGBB.',
    )
    logo = fields.Binary(string='Logo', attachment=True, help='Se muestra en el login y el dashboard de la app.')

    @api.model
    def get_active_config(self):
        config = self.search([], limit=1)
        if not config:
            config = self.create({})
        return config
