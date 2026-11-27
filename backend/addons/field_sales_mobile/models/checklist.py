# -*- coding: utf-8 -*-
from odoo import fields, models


class FieldSalesChecklistItem(models.Model):
    _name = 'field.sales.checklist.item'
    _description = 'Ítem de Checklist Agronómico'
    _order = 'sequence, id'

    name = fields.Char(string='Pregunta / Ítem', required=True)
    sequence = fields.Integer(default=10)
    active = fields.Boolean(default=True)


class FieldSalesVisitChecklistAnswer(models.Model):
    _name = 'field.sales.visit.checklist.answer'
    _description = 'Respuesta de Checklist - Visita'

    visit_id = fields.Many2one('field.sales.visit', string='Visita', required=True, ondelete='cascade')
    item_id = fields.Many2one('field.sales.checklist.item', string='Ítem', required=True)
    checked = fields.Boolean(string='Marcado')
