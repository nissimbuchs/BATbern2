#!/usr/bin/env bash
# Upload seed material PDFs to local MinIO
# Called by: make dev-seed-data
set -euo pipefail

MINIO_ALIAS="local-minio"
MINIO_URL="http://localhost:8450"
BUCKET="materials"

# Ensure mc alias exists
mc alias set "$MINIO_ALIAS" "$MINIO_URL" minioadmin minioadmin >/dev/null 2>&1

# Create minimal PDFs using Python
python3 -c "
def create_pdf(filepath, title, author, subtitle):
    stream = f'BT /F1 24 Tf 100 700 Td ({title}) Tj ET BT /F1 16 Tf 100 660 Td ({author}) Tj ET BT /F1 12 Tf 100 620 Td ({subtitle}) Tj ET'
    stream_bytes = stream.encode('latin-1')
    pdf = b'%PDF-1.4\n'
    pdf += b'1 0 obj << /Type /Catalog /Pages 2 0 R >> endobj\n'
    pdf += b'2 0 obj << /Type /Pages /Kids [3 0 R] /Count 1 >> endobj\n'
    pdf += b'3 0 obj << /Type /Page /Parent 2 0 R /MediaBox [0 0 595 842] /Contents 4 0 R /Resources << /Font << /F1 5 0 R >> >> >> endobj\n'
    pdf += f'4 0 obj << /Length {len(stream_bytes)} >> stream\n'.encode()
    pdf += stream_bytes
    pdf += b'\nendstream endobj\n'
    pdf += b'5 0 obj << /Type /Font /Subtype /Type1 /BaseFont /Helvetica >> endobj\n'
    pdf += b'xref\n0 6\n0000000000 65535 f \n0000000009 00000 n \n0000000058 00000 n \n0000000115 00000 n \n0000000266 00000 n \n0000000400 00000 n \n'
    pdf += b'trailer << /Size 6 /Root 1 0 R >>\nstartxref\n480\n%%EOF\n'
    with open(filepath, 'wb') as f:
        f.write(pdf)

create_pdf('/tmp/seed-digital-twins.pdf', 'Digital Twins in der Schweizer Bauindustrie', 'Dr. Anna Mueller - ETH Zurich', 'BATbern 2026')
create_pdf('/tmp/seed-smart-city.pdf', 'Smart City Bern: Digitale Stadtplanung', 'Lisa Schneider - Stadt Bern', 'BATbern 2026')
"

# Upload to MinIO
mc cp /tmp/seed-digital-twins.pdf "$MINIO_ALIAS/$BUCKET/materials/BATbern998/anna-mueller/digital-twins-bauindustrie.pdf" >/dev/null 2>&1
mc cp /tmp/seed-smart-city.pdf "$MINIO_ALIAS/$BUCKET/materials/BATbern998/lisa-schneider/smart-city-bern-stadtplanung.pdf" >/dev/null 2>&1

echo "  ✓ Uploaded 2 seed PDFs to MinIO"

# Cleanup
rm -f /tmp/seed-digital-twins.pdf /tmp/seed-smart-city.pdf
