# -*- coding: utf-8 -*-
from odoo import api, fields, models


class FieldSalesCatalogConfig(models.Model):
    _name = 'field.sales.catalog.config'
    _description = 'Configuración de Catálogo - App móvil'

    name = fields.Char(string='Nombre', default='Configuración de Catálogo')
    product_category_id = fields.Many2one(
        'product.category', string='Categoría de producto',
        help='Solo se sincronizarán a la app los productos de esta categoría '
             '(y sus subcategorías). Vacío = todos los productos vendibles.',
    )
    pricelist_id = fields.Many2one(
        'product.pricelist', string='Lista de precios',
        help='Precios que verá el vendedor en la app. Vacío = lista de precios pública/por defecto.',
    )
    show_stock = fields.Boolean(
        string='Mostrar stock disponible', default=True,
        help='Si está activo, la app muestra la cantidad disponible de cada producto '
             '(y avisa "sin stock") usando el Inventario de Odoo.',
    )

    @api.model
    def get_active_config(self):
        """Devuelve (o crea) el registro único de configuración del catálogo."""
        config = self.search([], limit=1)
        if not config:
            config = self.create({})
        return config
