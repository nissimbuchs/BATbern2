#!/bin/bash
# Validate database migration backward compatibility
# Usage: ./validate-migration.sh

set -e

echo "=========================================="
echo "Validating Database Migration Compatibility"
echo "=========================================="

VALIDATION_FAILED=false

# Find all pending migration files
MIGRATION_FILES=$(find . -path "*/db/migration/V*.sql" -type f 2>/dev/null | sort)

if [ -z "$MIGRATION_FILES" ]; then
    echo "No migration files found"
    echo "✓ Migration validation passed (no migrations to validate)"
    exit 0
fi

MIGRATION_COUNT=$(echo "$MIGRATION_FILES" | wc -l)
echo "Found $MIGRATION_COUNT migration files to validate"
echo ""

# Backward compatibility rules
echo "Checking backward compatibility rules..."
echo ""

# Rule 1: No DROP TABLE without IF EXISTS
echo "Rule 1: Checking for unsafe DROP TABLE statements..."
UNSAFE_DROPS=$(grep -rn "DROP TABLE" $MIGRATION_FILES | grep -v "IF EXISTS" | grep -v "^\s*--" || true)
if [ -n "$UNSAFE_DROPS" ]; then
    echo "❌ FAIL: Found DROP TABLE without IF EXISTS:"
    echo "$UNSAFE_DROPS"
    echo ""
    VALIDATION_FAILED=true
else
    echo "✓ PASS: All DROP TABLE statements use IF EXISTS"
    echo ""
fi

# Rule 2: No DROP COLUMN (warn - requires application compatibility)
echo "Rule 2: Checking for DROP COLUMN statements..."
DROP_COLUMNS=$(grep -rn "DROP COLUMN" $MIGRATION_FILES | grep -v "^\s*--" || true)
if [ -n "$DROP_COLUMNS" ]; then
    echo "⚠️  WARNING: Found DROP COLUMN statements:"
    echo "$DROP_COLUMNS"
    echo "Ensure application code is compatible with missing columns"
    echo ""
    # Don't fail, just warn
else
    echo "✓ PASS: No DROP COLUMN statements found"
    echo ""
fi

# Rule 3: No NOT NULL columns without DEFAULT
echo "Rule 3: Checking for NOT NULL columns without DEFAULT..."
NOT_NULL_NO_DEFAULT=$(grep -rn "ADD COLUMN.*NOT NULL" $MIGRATION_FILES | grep -v "DEFAULT" | grep -v "^\s*--" || true)
if [ -n "$NOT_NULL_NO_DEFAULT" ]; then
    echo "❌ FAIL: Found NOT NULL column without DEFAULT:"
    echo "$NOT_NULL_NO_DEFAULT"
    echo "This will fail if table has existing rows"
    echo ""
    VALIDATION_FAILED=true
else
    echo "✓ PASS: All NOT NULL columns have DEFAULT values"
    echo ""
fi

# Rule 4: No ALTER COLUMN TYPE (requires careful handling)
echo "Rule 4: Checking for column type changes..."
ALTER_TYPE=$(grep -rn "ALTER COLUMN.*TYPE" $MIGRATION_FILES | grep -v "^\s*--" || true)
if [ -n "$ALTER_TYPE" ]; then
    echo "⚠️  WARNING: Found column type changes:"
    echo "$ALTER_TYPE"
    echo "Ensure type changes are backward compatible"
    echo ""
    # Don't fail, just warn
else
    echo "✓ PASS: No column type changes found"
    echo ""
fi

# Rule 5: Check for proper constraint naming
echo "Rule 5: Checking constraint naming conventions..."
UNNAMED_CONSTRAINTS=$(grep -rn "ADD CONSTRAINT" $MIGRATION_FILES | grep -v "CONSTRAINT [a-zA-Z]" | grep -v "^\s*--" || true)
if [ -n "$UNNAMED_CONSTRAINTS" ]; then
    echo "⚠️  WARNING: Found constraints without explicit names:"
    echo "$UNNAMED_CONSTRAINTS"
    echo "Consider using explicit constraint names for easier rollback"
    echo ""
else
    echo "✓ PASS: All constraints have explicit names"
    echo ""
fi

# Rule 6: Check for USING clauses in data migrations
echo "Rule 6: Checking for data migrations..."
DATA_MIGRATIONS=$(grep -rn "UPDATE\|INSERT\|DELETE" $MIGRATION_FILES | grep -v "^\s*--" | head -5 || true)
if [ -n "$DATA_MIGRATIONS" ]; then
    echo "⚠️  WARNING: Found data migration statements:"
    echo "$DATA_MIGRATIONS"
    echo "Ensure data migrations are idempotent and backward compatible"
    echo ""
else
    echo "✓ PASS: No data migrations found"
    echo ""
fi

# Rollback validation
echo "=========================================="
echo "Validating Rollback Capability"
echo "=========================================="

# Check if rollback scripts exist
for migration in $MIGRATION_FILES; do
    VERSION=$(basename "$migration" | grep -oP 'V\K[0-9]+')
    MIGRATION_DIR=$(dirname "$migration")
    ROLLBACK_FILE="$MIGRATION_DIR/R${VERSION}__*.sql"

    if ! ls $ROLLBACK_FILE 1> /dev/null 2>&1; then
        echo "⚠️  WARNING: No rollback script found for $(basename $migration)"
        echo "Consider creating: R${VERSION}__rollback_$(basename $migration | sed 's/V[0-9]*__//')"
    else
        echo "✓ Rollback script exists for V${VERSION}"
    fi
done

echo ""
echo "=========================================="
echo "Migration Validation Summary"
echo "=========================================="

if [ "$VALIDATION_FAILED" = true ]; then
    echo "❌ MIGRATION VALIDATION FAILED"
    echo "Fix the issues above before promoting to production"
    exit 1
else
    echo "✅ MIGRATION VALIDATION PASSED"
    echo "Migrations are backward compatible"
    exit 0
fi
