#!/bin/bash
# Shared service definitions for the native-dev scripts.
#
# Sourced by start-all-native.sh and status-native.sh so the "which services run, and
# with how much heap" decision lives in exactly one place.
#
# Callers must set the *_PORT variables (derived from BASE_PORT) BEFORE calling
# service_port; the function body is evaluated at call time, so sourcing order is free.

# Resolve the Docker Compose command available on this host.
#
# Compose ships in two shapes: the v2 CLI plugin (`docker compose`, a subcommand) and the
# v1 standalone binary (`docker-compose`, hyphenated). Docker Desktop provides v2, so most
# dev machines never see the difference — but Synology DSM's Docker package ships ONLY the
# v1 binary and has no cli-plugins directory, and there `docker compose -f ... up -d` fails
# with "unknown shorthand flag: 'f' in -f".
#
# Echoes the working command, or an empty string when neither is present (callers should
# report that rather than emitting a broken command line).
compose_cmd() {
    if docker compose version >/dev/null 2>&1; then
        echo "docker compose"
    elif command -v docker-compose >/dev/null 2>&1; then
        echo "docker-compose"
    else
        echo ""
    fi
}

# Every service the platform can run, in port order. Used for "what is NOT running"
# reporting — do not reorder without checking the callers.
ALL_NATIVE_SERVICES="api-gateway company-user-management event-management speaker-coordination partner-coordination attendee-experience"

# Services started by default: all of them.
#
# Between 2026-08-08 and 2026-08-14 this defaulted to a 4-service subset (Speaker
# Coordination and Attendee Experience excluded, ~400-500 MB each) because the dev host
# was a 6 GB NAS. That box is being decommissioned, so the subset no longer buys anything
# — and a partial set is actively misleading: the API Gateway routes to the missing
# services and fails, which reads as a broken gateway rather than a service that was
# never started.
#
# To run a subset anyway (a genuinely small machine, or isolating one service), name it:
#   DEV_SERVICES="api-gateway company-user-management" make dev-native-up
DEV_SERVICES="${DEV_SERVICES:-$ALL_NATIVE_SERVICES}"

# Per-service max heap. Event Management carries the bulk of the domain data, then
# Company/User; the rest are thin. Explicit caps matter because the JVM otherwise
# defaults to MaxRAMPercentage=25 — several JVMs each claiming a quarter of RAM will
# swap-thrash any machine without ~16 GB to spare.
service_heap() {
    case "$1" in
        event-management)         echo "768m" ;;
        company-user-management)  echo "512m" ;;
        api-gateway)              echo "320m" ;;
        partner-coordination)     echo "320m" ;;
        *)                        echo "320m" ;;
    esac
}

# Gradle module path for a service name.
service_module() {
    case "$1" in
        api-gateway) echo ":api-gateway" ;;
        *)           echo ":services:$1-service" ;;
    esac
}

# Port for a service name. Requires the *_PORT variables to be set by the caller.
service_port() {
    case "$1" in
        api-gateway)             echo "${API_GATEWAY_PORT}" ;;
        company-user-management) echo "${COMPANY_USER_MGMT_PORT}" ;;
        event-management)        echo "${EVENT_MGMT_PORT}" ;;
        speaker-coordination)    echo "${SPEAKER_COORD_PORT}" ;;
        partner-coordination)    echo "${PARTNER_COORD_PORT}" ;;
        attendee-experience)     echo "${ATTENDEE_EXP_PORT}" ;;
    esac
}

# True when the named service is in the active selection.
service_enabled() {
    case " ${DEV_SERVICES} " in
        *" $1 "*) return 0 ;;
        *)        return 1 ;;
    esac
}
