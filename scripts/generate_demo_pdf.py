from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Table, TableStyle, Paragraph, Spacer
from reportlab.lib.units import inch

def generate_popular_pdf(output_path):
    doc = SimpleDocTemplate(output_path, pagesize=letter)
    elements = []
    styles = getSampleStyleSheet()
    
    # Custom Styles
    title_style = ParagraphStyle(
        'TitleStyle',
        parent=styles['Heading1'],
        fontSize=16,
        textColor=colors.HexColor("#003366"),
        alignment=1,
        spaceAfter=12
    )
    
    header_style = ParagraphStyle(
        'HeaderStyle',
        parent=styles['Normal'],
        fontSize=10,
        leading=12
    )

    # Header
    elements.append(Paragraph("BANCO POPULAR DOMINICANO", title_style))
    elements.append(Paragraph("Estado de Cuenta - Cuenta Corriente Personal", styles['Heading2']))
    elements.append(Spacer(1, 0.2 * inch))
    
    header_data = [
        ["Cliente:", "USUARIO DEMO FLOWTRACK"],
        ["Cuenta:", "000000000000123456789"],
        ["Periodo:", "01/05/2026 al 11/06/2026"],
        ["Moneda:", "PESOS DOMINICANOS (RD$)"]
    ]
    
    for row in header_data:
        elements.append(Paragraph(f"<b>{row[0]}</b> {row[1]}", header_style))
    
    elements.append(Spacer(1, 0.3 * inch))

    # Transactions Table
    data = [
        ["Fecha", "Descripción", "Referencia", "Débito", "Crédito", "Balance"]
    ]
    
    transactions = [
        ("01/05/2026", "NOMINA EMPRESA ABC - PAGO QUINCENA", "0001", "", "20,000.00", "20,000.00"),
        ("02/05/2026", "PAGO SERVICIO EDESUR 123456", "0002", "3,500.00", "", "16,500.00"),
        ("05/05/2026", "SUPERMERCADOS BRAVO - COMPRA SEMANAL", "0003", "4,250.50", "", "12,249.50"),
        ("10/05/2026", "RESTAURANTE EL TABLON - CENA FAMILIAR", "0004", "1,200.00", "", "11,049.50"),
        ("15/05/2026", "TRANSFERENCIA RECIBIDA - REEMBOLSO", "0005", "", "5,540.00", "16,589.50"),
        ("15/05/2026", "PAGO INTERNET CLARO - MAYO 2026", "0006", "2,100.40", "", "14,489.10"),
        ("20/05/2026", "ESTACION SHELL - COMBUSTIBLE", "0007", "2,500.00", "", "11,989.10"),
        ("25/05/2026", "FARMACIA CAROL - MEDICAMENTOS", "0008", "850.10", "", "11,139.00"),
        ("30/05/2026", "DEPOSITO EFECTIVO CAJERO - AHORRO", "0009", "", "5,000.00", "16,139.00"),
        ("01/06/2026", "NETFLIX.COM - SUSCRIPCION MENSUAL", "0010", "500.00", "", "15,639.00"),
        ("05/06/2026", "UBER TRIP - TRANSPORTE TRABAJO", "0011", "342.00", "", "15,297.00"),
        ("10/06/2026", "RETIRO CAJERO AUTOMATICO POPULAR", "0012", "2,400.00", "", "12,897.00"),
    ]
    
    data.extend(transactions)

    table = Table(data, colWidths=[0.8*inch, 2.5*inch, 0.8*inch, 1*inch, 1*inch, 1.2*inch])
    table.setStyle(TableStyle([
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor("#003366")),
        ('TEXTCOLOR', (0, 0), (-1, 0), colors.whitesmoke),
        ('ALIGN', (0, 0), (-1, -1), 'CENTER'),
        ('ALIGN', (1, 1), (1, -1), 'LEFT'),
        ('ALIGN', (3, 1), (-1, -1), 'RIGHT'),
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('FONTSIZE', (0, 0), (-1, 0), 10),
        ('BOTTOMPADDING', (0, 0), (-1, 0), 12),
        ('BACKGROUND', (0, 1), (-1, -1), colors.white),
        ('GRID', (0, 0), (-1, -1), 1, colors.grey),
        ('FONTSIZE', (0, 1), (-1, -1), 9),
    ]))
    
    elements.append(table)
    elements.append(Spacer(1, 0.5 * inch))

    # Summary
    summary_data = [
        ["Resumen del Periodo", ""],
        ["Total Ingresos (Créditos):", "RD$ 30,540.00"],
        ["Total Egresos (Débitos):", "RD$ 17,643.00"],
        ["Balance Final:", "RD$ 12,897.00"]
    ]
    
    summary_table = Table(summary_data, colWidths=[3*inch, 2*inch])
    summary_table.setStyle(TableStyle([
        ('FONTNAME', (0, 0), (-1, 0), 'Helvetica-Bold'),
        ('ALIGN', (1, 0), (1, -1), 'RIGHT'),
        ('LINEBELOW', (0, 0), (-1, 0), 1, colors.black),
        ('FONTNAME', (0, -1), (-1, -1), 'Helvetica-Bold'),
        ('TOPPADDING', (0, 1), (-1, -1), 5),
    ]))
    
    elements.append(summary_table)
    
    # Build
    doc.build(elements)
    print(f"PDF generado exitosamente en: {output_path}")

if __name__ == "__main__":
    import os
    target_path = os.path.join("docs", "04-demo", "popular_demo_mayo_junio_2026.pdf")
    os.makedirs(os.path.dirname(target_path), exist_ok=True)
    generate_popular_pdf(target_path)
