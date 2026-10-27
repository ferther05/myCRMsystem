"""Create a visibly synthetic PDF used only by the local East-team demo seed."""
from pathlib import Path
from reportlab.pdfgen import canvas
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.cidfonts import UnicodeCIDFont
from pypdf import PdfReader

root = Path(__file__).resolve().parents[1]
target = root / 'output/contract-files/demo-east-20261022/demo-only.pdf'
target.parent.mkdir(parents=True, exist_ok=True)
if not target.exists():
    pdfmetrics.registerFont(UnicodeCIDFont('STSong-Light'))
    pdf = canvas.Canvas(str(target), pagesize=(595, 842))
    pdf.setTitle('CRM Demo Only - No Real Contract')
    pdf.setFillColorRGB(.75, .12, .12)
    pdf.setFont('STSong-Light', 23)
    pdf.drawString(48, 765, '演示资料 · 无真实合同效力')
    pdf.setFillColorRGB(.12, .18, .28)
    pdf.setFont('STSong-Light', 13)
    lines = [
        '批次：CRM-DEMO-EAST-20261022',
        '用途：CRM 合同上传、下载、资料核验与退回流程测试。',
        '本文件无真实交易条款、签章、签名或法律承诺。',
        '页面中的登记、核验及赢单状态均为合成演示场景。',
        '请勿将本文件或演示状态用于真实交易与结算。',
        '实际业务请登记双方确认的真实合同并上传实际签署资料。',
    ]
    for index, line in enumerate(lines):
        pdf.drawString(48, 697 - index * 34, line)
    pdf.setStrokeColorRGB(.8, .84, .9)
    pdf.line(48, 440, 547, 440)
    pdf.setFont('Helvetica', 11)
    pdf.drawString(48, 415, 'SYNTHETIC DEMO ONLY - NOT A SIGNED CONTRACT')
    pdf.save()
reader = PdfReader(str(target))
assert len(reader.pages) == 1
assert 'SYNTHETIC DEMO ONLY' in reader.pages[0].extract_text()
print(target.as_posix())
