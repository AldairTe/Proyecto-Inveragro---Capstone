{
    'name': 'Fuerza de Ventas Móvil',
    'version': '13.0.1.0.0',
    'category': 'Sales',
    'summary': 'Gestión de vendedores, clientes y visitas para fuerza de ventas móvil',
    'description': """
Fuerza de Ventas Móvil
=======================
Módulo para gestionar vendedores de campo, sus clientes asignados y las
visitas que registran desde una app móvil (con fotos, videos y GPS).

Incluye una API REST propia para que la app móvil se conecte a Odoo sin
necesidad de usuarios de Odoo para cada vendedor.
    """,
    'author': 'Aldair Tello',
    'depends': ['base', 'hr', 'mail', 'sale', 'stock'],
    'data': [
        'security/field_sales_security.xml',
        'security/ir.model.access.csv',
        'views/hr_employee_views.xml',
        'views/customer_views.xml',
        'views/visit_views.xml',
        'views/dashboard_views.xml',
        'views/activation_views.xml',
        'views/attendance_views.xml',
        'views/catalog_views.xml',
        'views/order_views.xml',
        'views/checklist_views.xml',
        'views/signature_views.xml',
        'views/route_views.xml',
        'views/branding_views.xml',
        'views/location_views.xml',
        'views/live_map_template.xml',
        'views/menu_views.xml',
        'data/location_cron.xml',
    ],
    'installable': True,
    'application': True,
    'license': 'LGPL-3',
}
