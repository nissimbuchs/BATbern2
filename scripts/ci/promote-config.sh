#!/bin/bash
# Promote configuration from source to target environment
# Usage: ./promote-config.sh <source_env> <target_env>

set -e

SOURCE_ENV=$1
TARGET_ENV=$2

if [ -z "$SOURCE_ENV" ] || [ -z "$TARGET_ENV" ]; then
    echo "Usage: $0 <source_env> <target_env>"
    exit 1
fi

echo "=========================================="
echo "Promoting Configuration"
echo "Source: $SOURCE_ENV"
echo "Target: $TARGET_ENV"
echo "=========================================="

# Validate config compatibility before promotion
validate_config_compatibility() {
    local source_config_file=$1
    local target_env=$2

    echo "Validating configuration compatibility..."

    # Check if required parameters exist
    required_params=(
        "database/connection-string"
        "database/pool-size"
        "redis/cluster-endpoint"
        "api-gateway/cors-origins"
    )

    for param in "${required_params[@]}"; do
        if ! jq -e ".[] | select(.[0] | contains(\"$param\"))" "$source_config_file" > /dev/null; then
            echo "ERROR: Required parameter $param not found in source config"
            exit 1
        fi
    done

    echo "✓ Configuration compatibility validated"
}

# Export parameters from source environment
export_source_config() {
    local source_env=$1
    local output_file="source_config_${source_env}.json"

    echo "Exporting configuration from $source_env..."

    aws ssm get-parameters-by-path \
        --path "/batbern/$source_env/" \
        --recursive \
        --with-decryption \
        --query 'Parameters[].[Name,Value,Type]' \
        --output json > "$output_file"

    echo "✓ Exported $(jq '. | length' $output_file) parameters from $source_env"
    echo "$output_file"
}

# Promote parameters to target environment
promote_parameters() {
    local source_config_file=$1
    local target_env=$2

    echo "Promoting parameters to $target_env..."

    # Read each parameter and promote it
    jq -r '.[] | @tsv' "$source_config_file" | while IFS=$'\t' read -r name value type; do
        # Replace source env with target env in parameter name
        target_name=$(echo "$name" | sed "s|/$SOURCE_ENV/|/$target_env/|")

        echo "Promoting: $target_name"

        aws ssm put-parameter \
            --name "$target_name" \
            --value "$value" \
            --type "$type" \
            --overwrite \
            --tags Key=Environment,Value=$target_env Key=PromotedFrom,Value=$SOURCE_ENV \
            --no-cli-pager > /dev/null

    done

    echo "✓ Parameters promoted successfully"
}

# Main execution
SOURCE_CONFIG_FILE=$(export_source_config "$SOURCE_ENV")

validate_config_compatibility "$SOURCE_CONFIG_FILE" "$TARGET_ENV"

promote_parameters "$SOURCE_CONFIG_FILE" "$TARGET_ENV"

# Clean up temporary files
rm -f "$SOURCE_CONFIG_FILE"

echo ""
echo "=========================================="
echo "✅ Configuration promotion complete!"
echo "=========================================="
