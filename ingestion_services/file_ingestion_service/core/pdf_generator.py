"""
PDF Generator for Sejong Tablet Press Production Reports (MC081).
Generates high-precision two-page PDF reports matching the Sejong machine report format.
"""

import os
import logging
from datetime import datetime
from typing import Dict, Any, Optional

from reportlab.lib.pagesizes import letter
from reportlab.lib import colors
from reportlab.lib.units import inch
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, PageBreak, KeepTogether
)
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.pdfgen import canvas

logger = logging.getLogger("compression.pdf_generator")


class NumberedCanvas(canvas.Canvas):
    """Two-pass canvas to add running footer with page numbers."""

    def __init__(self, *args, **kwargs):
        super().__init__(*args, **kwargs)
        self._saved_page_states = []

    def showPage(self):
        self._saved_page_states.append(dict(self.__dict__))
        self._startPage()

    def save(self):
        num_pages = len(self._saved_page_states)
        for state in self._saved_page_states:
            self.__dict__.update(state)
            self.draw_page_decorations(num_pages)
            super().showPage()
        super().save()

    def draw_page_decorations(self, page_count: int):
        self.saveState()
        self.setFont("Helvetica", 8)
        self.setFillColor(colors.HexColor("#555555"))
        # Footer
        footer_text = f"ADAVIS IIoT Platform - Equipment MC081 (Stage 4 Compression) | Page {self._pageNumber} of {page_count}"
        self.drawCentredString(letter[0] / 2.0, 20, footer_text)
        self.restoreState()


def generate_production_report_pdf(report_data: Dict[str, Any], output_pdf_path: str) -> str:
    """
    Generates a 2-page Sejong Tablet Press Report PDF from parsed report data.
    """
    os.makedirs(os.path.dirname(os.path.abspath(output_pdf_path)), exist_ok=True)

    doc = SimpleDocTemplate(
        output_pdf_path,
        pagesize=letter,
        leftMargin=36,
        rightMargin=36,
        topMargin=36,
        bottomMargin=36
    )

    styles = getSampleStyleSheet()
    
    # Custom typography styles
    title_style = ParagraphStyle(
        "ReportTitle",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=13,
        leading=16,
        alignment=1,  # Center
        textColor=colors.HexColor("#1A2B4C")
    )
    
    header_sub_style = ParagraphStyle(
        "HeaderSub",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=9,
        leading=11,
        textColor=colors.HexColor("#333333")
    )

    section_heading_style = ParagraphStyle(
        "SectionHeading",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=10,
        leading=12,
        textColor=colors.HexColor("#1A2B4C"),
        spaceAfter=4
    )

    cell_bold = ParagraphStyle(
        "CellBold",
        parent=styles["Normal"],
        fontName="Helvetica-Bold",
        fontSize=8,
        leading=10,
        textColor=colors.HexColor("#222222")
    )

    cell_normal = ParagraphStyle(
        "CellNormal",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=8,
        leading=10,
        textColor=colors.HexColor("#222222")
    )

    cell_center = ParagraphStyle(
        "CellCenter",
        parent=styles["Normal"],
        fontName="Helvetica",
        fontSize=8,
        leading=10,
        alignment=1,
        textColor=colors.HexColor("#222222")
    )

    binfo = report_data.get("batchInfo", {})
    recipe = report_data.get("recipeSettings", {})
    limits = recipe.get("controlLimits", {})
    pressure = report_data.get("pressureData", {})
    op_vals = report_data.get("operationValues", {})
    counters = report_data.get("tabletCounters", {})
    signatures = report_data.get("signatures", {})
    meta = report_data.get("metadata", {})

    story = []

    # ==========================================
    # PAGE 1: Product Info, Settings, Pressure
    # ==========================================
    header_table_data = [
        [
            Paragraph("<b>SEJONG TABLET PRESS REPORT</b>", title_style),
            Paragraph("(1/2)", ParagraphStyle("P1Tag", parent=cell_bold, alignment=2))
        ],
        [
            Paragraph(f"Software version ( {meta.get('softwareVersion', '2.0')} )", header_sub_style),
            Paragraph("", header_sub_style)
        ],
        [
            Paragraph("<b>PRODUCTION REPORT</b>", ParagraphStyle("SubTitle", parent=title_style, fontSize=11, textColor=colors.HexColor("#2C3E50"))),
            Paragraph("", header_sub_style)
        ]
    ]
    header_table = Table(header_table_data, colWidths=[480, 60])
    header_table.setStyle(TableStyle([
        ('SPAN', (0, 0), (0, 0)),
        ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 1),
        ('TOPPADDING', (0, 0), (-1, -1), 1),
    ]))
    story.append(header_table)
    story.append(Spacer(1, 6))

    # Product Information Section
    story.append(Paragraph("<b>Product Information</b>", section_heading_style))
    prod_info_data = [
        [Paragraph("Station No :", cell_bold), Paragraph(str(binfo.get("stationNo", "Station 1")), cell_normal), Paragraph("", cell_normal), Paragraph("", cell_normal)],
        [Paragraph("Machine Name :", cell_bold), Paragraph(str(binfo.get("machineName", "MC081 SEJONG 49D")), cell_normal), Paragraph("Product Name :", cell_bold), Paragraph(str(binfo.get("productName", "")), cell_normal)],
        [Paragraph("User ID :", cell_bold), Paragraph(str(binfo.get("userId", "")), cell_normal), Paragraph("Batch NO. :", cell_bold), Paragraph(str(binfo.get("batchNumber", "")), cell_normal)],
        [Paragraph("Print Interval :", cell_bold), Paragraph(f"{binfo.get('printInterval', '')} Tabs", cell_normal), Paragraph("Running Time :", cell_bold), Paragraph(str(binfo.get("runningTime", "")), cell_normal)],
        [Paragraph("Total Counter :", cell_bold), Paragraph(f"{binfo.get('totalCounter', '')} Tabs", cell_normal), Paragraph("Total Running Time :", cell_bold), Paragraph(str(binfo.get("totalRunningTime", "")), cell_normal)],
    ]
    t_prod = Table(prod_info_data, colWidths=[110, 160, 110, 160])
    t_prod.setStyle(TableStyle([
        ('BOX', (0, 0), (-1, -1), 1, colors.HexColor("#A0AEC0")),
        ('INNERGRID', (0, 0), (-1, -1), 0.5, colors.HexColor("#E2E8F0")),
        ('BACKGROUND', (0, 0), (0, -1), colors.HexColor("#F7FAFC")),
        ('BACKGROUND', (2, 0), (2, -1), colors.HexColor("#F7FAFC")),
        ('TOPPADDING', (0, 0), (-1, -1), 3),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
    ]))
    story.append(t_prod)
    story.append(Spacer(1, 8))

    # Setting Values Section
    story.append(Paragraph("<b>Setting Value</b>", section_heading_style))
    feeder = recipe.get("feeder", {})
    hydra = recipe.get("hydraulicPressureLimits", {})
    oil = recipe.get("oilLubrication", {})
    s1 = oil.get("upperPunchS1", {})
    s2 = oil.get("lowerPunchS2", {})
    s3 = oil.get("lowerHeadS3", {})

    setting_data = [
        [Paragraph("Feeder :", cell_bold), Paragraph(f"Auto ; {feeder.get('autoPercent', '')} %", cell_normal), Paragraph(f"Manual ; {feeder.get('manualRpm', '')} RPM", cell_normal), Paragraph("", cell_normal)],
        [Paragraph("Filling Cam :", cell_bold), Paragraph(str(recipe.get("fillingCam", "")), cell_normal), Paragraph("Target Quantity :", cell_bold), Paragraph(f"{recipe.get('targetQuantity', '')} Tabs", cell_normal)],
        [Paragraph("Air Pressure Low Limit :", cell_bold), Paragraph(f"{recipe.get('airPressureLowLimitKpa', '')} Kpa", cell_normal), Paragraph("Hydraulic High Limit :", cell_bold), Paragraph(f"{hydra.get('highLimitMpa', '')} Mpa (Low: {hydra.get('lowLimitMpa', '')} Mpa)", cell_normal)],
        [Paragraph("Oil Lubrication S1 :", cell_bold), Paragraph(f"Interval: {s1.get('intervalMin', '')} Min", cell_normal), Paragraph(f"Supply: {s1.get('supplySec', '')} Sec", cell_normal), Paragraph("Upper Punch", cell_normal)],
        [Paragraph("Oil Lubrication S2 :", cell_bold), Paragraph(f"Interval: {s2.get('intervalMin', '')} Min", cell_normal), Paragraph(f"Supply: {s2.get('supplySec', '')} Sec", cell_normal), Paragraph("Lower Punch", cell_normal)],
        [Paragraph("Oil Lubrication S3 :", cell_bold), Paragraph(f"Interval: {s3.get('intervalMin', '')} Min", cell_normal), Paragraph(f"Supply: {s3.get('supplySec', '')} Sec", cell_normal), Paragraph("Lower Head", cell_normal)],
        [Paragraph("Powder Supply Time :", cell_bold), Paragraph(f"{recipe.get('powderSupplyTimeSec', '')} Sec", cell_normal), Paragraph("Initial Reject Time :", cell_bold), Paragraph(f"{recipe.get('initialRejectTimeSec', '')} Sec", cell_normal)],
    ]
    t_setting = Table(setting_data, colWidths=[120, 150, 130, 140])
    t_setting.setStyle(TableStyle([
        ('BOX', (0, 0), (-1, -1), 1, colors.HexColor("#A0AEC0")),
        ('INNERGRID', (0, 0), (-1, -1), 0.5, colors.HexColor("#E2E8F0")),
        ('BACKGROUND', (0, 0), (0, -1), colors.HexColor("#F7FAFC")),
        ('TOPPADDING', (0, 0), (-1, -1), 2),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 2),
    ]))
    story.append(t_setting)
    story.append(Spacer(1, 8))

    # Limits & Stop Conditions Table
    story.append(Paragraph("<b>Control Limits & Stop Conditions</b>", section_heading_style))
    hsp = limits.get("hsp", {})
    hep = limits.get("hep", {})
    hcp = limits.get("hcp", {})
    ref = limits.get("ref", {})
    lcp = limits.get("lcp", {})
    lep = limits.get("lep", {})
    lsp = limits.get("lsp", {})
    sd = limits.get("sdLimit", {})
    pre_hsp = limits.get("preHsp", {})

    limits_data = [
        [Paragraph("Parameter", cell_bold), Paragraph("% Setting", cell_bold), Paragraph("kN Limit", cell_bold), Paragraph("Stop Condition", cell_bold)],
        [Paragraph("HSP (High Stop Pressure)", cell_normal), Paragraph(f"{hsp.get('percent', '')} %", cell_center), Paragraph(f"{hsp.get('kn', '')} kN", cell_center), Paragraph(f"Stop: {hsp.get('stop', '')}", cell_normal)],
        [Paragraph("HEP (High Error Pressure)", cell_normal), Paragraph(f"{hep.get('percent', '')} %", cell_center), Paragraph(f"{hep.get('kn', '')} kN", cell_center), Paragraph(f"{hep.get('rot', '')} Rot / {hep.get('tabs', '')} Tabs", cell_normal)],
        [Paragraph("HCP (High Control Pressure)", cell_normal), Paragraph(f"{hcp.get('percent', '')} %", cell_center), Paragraph(f"{hcp.get('kn', '')} kN", cell_center), Paragraph(f"{hcp.get('times', '')} Times", cell_normal)],
        [Paragraph("Ref (Reference Pressure)", cell_normal), Paragraph("-", cell_center), Paragraph(f"{ref.get('kn', '')} kN", cell_center), Paragraph("-", cell_normal)],
        [Paragraph("LCP (Low Control Pressure)", cell_normal), Paragraph(f"{lcp.get('percent', '')} %", cell_center), Paragraph(f"{lcp.get('kn', '')} kN", cell_center), Paragraph(f"{lcp.get('times', '')} Times", cell_normal)],
        [Paragraph("LEP (Low Error Pressure)", cell_normal), Paragraph(f"{lep.get('percent', '')} %", cell_center), Paragraph(f"{lep.get('kn', '')} kN", cell_center), Paragraph(f"{lep.get('rot', '')} Rot / {lep.get('tabs', '')} Tabs", cell_normal)],
        [Paragraph("LSP (Low Stop Pressure)", cell_normal), Paragraph(f"{lsp.get('percent', '')} %", cell_center), Paragraph(f"{lsp.get('kn', '')} kN", cell_center), Paragraph(f"Stop: {lsp.get('stop', '')}", cell_normal)],
        [Paragraph("SD Limit", cell_normal), Paragraph(f"{sd.get('percent', '')} %", cell_center), Paragraph("-", cell_center), Paragraph(f"Stop: {sd.get('stop', '')}", cell_normal)],
        [Paragraph("Pre HSP", cell_normal), Paragraph("-", cell_center), Paragraph(f"{pre_hsp.get('kn', '')} kN", cell_center), Paragraph(f"Stop: {pre_hsp.get('stop', '')}", cell_normal)],
    ]
    t_limits = Table(limits_data, colWidths=[180, 90, 110, 160])
    t_limits.setStyle(TableStyle([
        ('BOX', (0, 0), (-1, -1), 1, colors.HexColor("#A0AEC0")),
        ('INNERGRID', (0, 0), (-1, -1), 0.5, colors.HexColor("#E2E8F0")),
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor("#EDF2F7")),
        ('TOPPADDING', (0, 0), (-1, -1), 2),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 2),
    ]))
    story.append(t_limits)
    story.append(Spacer(1, 8))

    # Pressure Data Section
    story.append(Paragraph("<b>Pressure Data</b>", section_heading_style))
    pp = pressure.get("prePressure", {})
    mp = pressure.get("mainPressure", {})
    adj = pressure.get("fillingDepthAdjustments", {})

    press_data = [
        [Paragraph("Section", cell_bold), Paragraph("Mean (kN)", cell_bold), Paragraph("SD (%)", cell_bold), Paragraph("Min (kN) [Punch]", cell_bold), Paragraph("Max (kN) [Punch]", cell_bold)],
        [
            Paragraph("Pre Pressure", cell_normal),
            Paragraph(f"{pp.get('meanKn', '')} kN", cell_center),
            Paragraph(f"{pp.get('sdPercent', '')} %", cell_center),
            Paragraph(f"{pp.get('minKn', '')} kN [#{pp.get('minPunchNo', '')}]", cell_center),
            Paragraph(f"{pp.get('maxKn', '')} kN [#{pp.get('maxPunchNo', '')}]", cell_center),
        ],
        [
            Paragraph("Main Pressure", cell_normal),
            Paragraph(f"{mp.get('meanKn', '')} kN", cell_center),
            Paragraph(f"{mp.get('sdPercent', '')} %", cell_center),
            Paragraph(f"{mp.get('minKn', '')} kN [#{mp.get('minPunchNo', '')}]", cell_center),
            Paragraph(f"{mp.get('maxKn', '')} kN [#{mp.get('maxPunchNo', '')}]", cell_center),
        ],
        [
            Paragraph("Filling Depth Adjustments", cell_normal),
            Paragraph(f"Increase: {adj.get('increaseTimes', 0)} times", cell_normal),
            Paragraph(f"Decrease: {adj.get('decreaseTimes', 0)} times", cell_normal),
            Paragraph("", cell_normal),
            Paragraph("", cell_normal)
        ]
    ]
    t_press = Table(press_data, colWidths=[140, 100, 80, 110, 110])
    t_press.setStyle(TableStyle([
        ('BOX', (0, 0), (-1, -1), 1, colors.HexColor("#A0AEC0")),
        ('INNERGRID', (0, 0), (-1, -1), 0.5, colors.HexColor("#E2E8F0")),
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor("#EDF2F7")),
        ('SPAN', (1, 3), (2, 3)),
        ('SPAN', (3, 3), (4, 3)),
        ('TOPPADDING', (0, 0), (-1, -1), 2),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 2),
    ]))
    story.append(t_press)
    story.append(Spacer(1, 10))

    # Page 1 Signatures Block
    sig_p1 = [
        [Paragraph(f"Date: {signatures.get('reportDate', '')}", cell_normal), Paragraph(f"Operator: {signatures.get('operatorName', '')}", cell_bold), Paragraph("Signature: ______________________", cell_normal)]
    ]
    t_sig1 = Table(sig_p1, colWidths=[180, 180, 180])
    t_sig1.setStyle(TableStyle([
        ('BOX', (0, 0), (-1, -1), 1, colors.HexColor("#CBD5E0")),
        ('BACKGROUND', (0, 0), (-1, -1), colors.HexColor("#F8FAFC")),
        ('TOPPADDING', (0, 0), (-1, -1), 4),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
    ]))
    story.append(t_sig1)

    # ==========================================
    # PAGE BREAK -> PAGE 2
    # ==========================================
    story.append(PageBreak())

    header_p2 = [
        [
            Paragraph("<b>SEJONG TABLET PRESS REPORT</b>", title_style),
            Paragraph("(2/2)", ParagraphStyle("P2Tag", parent=cell_bold, alignment=2))
        ],
        [
            Paragraph(f"Software version ( {meta.get('softwareVersion', '2.0')} )", header_sub_style),
            Paragraph("", header_sub_style)
        ],
        [
            Paragraph("<b>PRODUCTION REPORT - OPERATIONS & TABLET DATA</b>", ParagraphStyle("SubTitle2", parent=title_style, fontSize=11, textColor=colors.HexColor("#2C3E50"))),
            Paragraph("", header_sub_style)
        ]
    ]
    t_head2 = Table(header_p2, colWidths=[480, 60])
    t_head2.setStyle(TableStyle([
        ('SPAN', (0, 0), (0, 0)),
        ('VALIGN', (0, 0), (-1, -1), 'MIDDLE'),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 1),
        ('TOPPADDING', (0, 0), (-1, -1), 1),
    ]))
    story.append(t_head2)
    story.append(Spacer(1, 6))

    # Operation Values Section
    story.append(Paragraph("<b>Operation Value</b>", section_heading_style))
    op_feeder = op_vals.get("feeder", {})
    op_pre = op_vals.get("prePressure", {})
    op_main = op_vals.get("mainPressure", {})
    op_oil = op_vals.get("lubricationRemainingMin", {})
    aux = op_vals.get("auxiliaryStatus", {})

    op_data = [
        [Paragraph("Disk Speed :", cell_bold), Paragraph(f"{op_vals.get('diskSpeedRpm', '')} RPM", cell_normal), Paragraph("Capacity :", cell_bold), Paragraph(f"{op_vals.get('capacityTabsPerHour', '')} Tabs/hour", cell_normal)],
        [Paragraph("Feeder Status :", cell_bold), Paragraph(str(op_feeder.get("status", "AUTO")), cell_normal), Paragraph("Feeder Speed :", cell_bold), Paragraph(f"{op_feeder.get('speedRpm', '')} RPM", cell_normal)],
        [Paragraph("Pre-Pressure Thickness :", cell_bold), Paragraph(f"{op_pre.get('thicknessMm', '')} mm", cell_normal), Paragraph("Lower Punch Position :", cell_bold), Paragraph(f"{op_pre.get('lowerPunchPositionMm', '')} mm (Penet: {op_pre.get('penetrationDepthMm', '')} mm)", cell_normal)],
        [Paragraph("Main-Pressure Thickness :", cell_bold), Paragraph(f"{op_main.get('thicknessMm', '')} mm", cell_normal), Paragraph("Lower Punch Position :", cell_bold), Paragraph(f"{op_main.get('lowerPunchPositionMm', '')} mm (Penet: {op_main.get('penetrationDepthMm', '')} mm)", cell_normal)],
        [Paragraph("Filling Depth :", cell_bold), Paragraph(f"{op_vals.get('fillingDepthMm', '')} mm", cell_normal), Paragraph("Current Cam :", cell_bold), Paragraph(str(op_vals.get("currentCam", "")), cell_normal)],
        [Paragraph("Main Air Pressure :", cell_bold), Paragraph(f"{op_vals.get('mainAirPressureKpa', '')} Kpa", cell_normal), Paragraph("Hydraulic Pressure :", cell_bold), Paragraph(f"{op_vals.get('hydraulicPressureMpa', '')} Mpa", cell_normal)],
        [Paragraph("Oil S1 Remain Time :", cell_bold), Paragraph(f"{op_oil.get('upperPunchS1', '')} Min", cell_normal), Paragraph("Oil S2 / S3 Remain :", cell_bold), Paragraph(f"S2: {op_oil.get('lowerPunchS2', '')} Min | S3: {op_oil.get('lowerHeadS3', '')} Min", cell_normal)],
        [Paragraph("Powder Status :", cell_bold), Paragraph(str(aux.get("powderStatus", "Enable")), cell_normal), Paragraph("Dust Collector :", cell_bold), Paragraph(str(aux.get("dustCollector", "ON")), cell_normal)],
        [Paragraph("Initial Reject :", cell_bold), Paragraph(str(aux.get("initialReject", "ON")), cell_normal), Paragraph("Buzzer :", cell_bold), Paragraph(str(aux.get("buzzer", "ON")), cell_normal)],
    ]
    t_op = Table(op_data, colWidths=[125, 145, 125, 145])
    t_op.setStyle(TableStyle([
        ('BOX', (0, 0), (-1, -1), 1, colors.HexColor("#A0AEC0")),
        ('INNERGRID', (0, 0), (-1, -1), 0.5, colors.HexColor("#E2E8F0")),
        ('BACKGROUND', (0, 0), (0, -1), colors.HexColor("#F7FAFC")),
        ('BACKGROUND', (2, 0), (2, -1), colors.HexColor("#F7FAFC")),
        ('TOPPADDING', (0, 0), (-1, -1), 2),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 2),
    ]))
    story.append(t_op)
    story.append(Spacer(1, 10))

    # Tablet Data & Counters Section
    story.append(Paragraph("<b>Tablet Data & Production Counters</b>", section_heading_style))
    hep_obj = counters.get("hep", {})
    lep_obj = counters.get("lep", {})
    good_obj = counters.get("good", {})

    tablet_data = [
        [Paragraph("Metric", cell_bold), Paragraph("Value / Count (Tabs)", cell_bold), Paragraph("Percentage / Details", cell_bold)],
        [Paragraph("<b>Total Counter</b>", cell_normal), Paragraph(f"<b>{counters.get('totalCounter', 0):,} Tabs</b>", cell_bold), Paragraph("100% Machine Processed", cell_normal)],
        [Paragraph("A.W.C. Counter", cell_normal), Paragraph(f"{counters.get('awcCounter', 0):,} Tabs", cell_normal), Paragraph("Automatic Weight Controlled", cell_normal)],
        [Paragraph("HEP (High Error Punch Reject)", cell_normal), Paragraph(f"{hep_obj.get('count', 0):,} Tabs", cell_normal), Paragraph(str(hep_obj.get("raw", "")), cell_normal)],
        [Paragraph("LEP (Low Error Punch Reject)", cell_normal), Paragraph(f"{lep_obj.get('count', 0):,} Tabs", cell_normal), Paragraph(str(lep_obj.get("raw", "")), cell_normal)],
        [Paragraph("<b>GOOD Tablets</b>", cell_bold), Paragraph(f"<b>{good_obj.get('count', 0):,} Tabs</b>", ParagraphStyle("GoodText", parent=cell_bold, textColor=colors.HexColor("#166534"))), Paragraph(str(good_obj.get("raw", "")), cell_bold)],
    ]
    t_tab = Table(tablet_data, colWidths=[180, 180, 180])
    t_tab.setStyle(TableStyle([
        ('BOX', (0, 0), (-1, -1), 1, colors.HexColor("#A0AEC0")),
        ('INNERGRID', (0, 0), (-1, -1), 0.5, colors.HexColor("#E2E8F0")),
        ('BACKGROUND', (0, 0), (-1, 0), colors.HexColor("#EDF2F7")),
        ('BACKGROUND', (0, 5), (-1, 5), colors.HexColor("#F0FDF4")),
        ('TOPPADDING', (0, 0), (-1, -1), 3),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 3),
    ]))
    story.append(t_tab)
    story.append(Spacer(1, 14))

    # Page 2 Final Signatures Block
    sig_p2 = [
        [Paragraph(f"Date: {signatures.get('reportDate', '')}", cell_normal), Paragraph(f"Operator: {signatures.get('operatorName', '')}", cell_bold), Paragraph("Signature: ______________________", cell_normal)]
    ]
    t_sig2 = Table(sig_p2, colWidths=[180, 180, 180])
    t_sig2.setStyle(TableStyle([
        ('BOX', (0, 0), (-1, -1), 1, colors.HexColor("#CBD5E0")),
        ('BACKGROUND', (0, 0), (-1, -1), colors.HexColor("#F8FAFC")),
        ('TOPPADDING', (0, 0), (-1, -1), 4),
        ('BOTTOMPADDING', (0, 0), (-1, -1), 4),
    ]))
    story.append(t_sig2)

    # Build document
    doc.build(story, canvasmaker=NumberedCanvas)
    logger.info(f"Generated Sejong production report PDF at: {output_pdf_path}")
    return output_pdf_path
