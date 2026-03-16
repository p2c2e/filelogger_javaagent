#!/bin/bash
# ---------------------------------------------------------------
# setup.sh - Build the file-logger-agent and run the demo
# ---------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEMO_SRC="${SCRIPT_DIR}/demo/SampleApp.java"
DEMO_OUT="${SCRIPT_DIR}/demo"
LOG_FILE="${1:-/tmp/files.txt}"

echo "============================================"
echo " File Logger Agent - Setup and Demo"
echo "============================================"
echo ""

# --- Step 1: Build the agent ---
echo "[1/4] Building agent JAR with Maven..."
mvn -f "${SCRIPT_DIR}/pom.xml" clean package -q
AGENT_JAR=$(ls "${SCRIPT_DIR}"/target/file-logger-javaagent-*.jar 2>/dev/null | head -1)
if [ -z "${AGENT_JAR}" ]; then
    echo "ERROR: No agent JAR found in ${SCRIPT_DIR}/target/" >&2
    exit 1
fi
echo "      Built: ${AGENT_JAR}"

# --- Step 2: Compile the demo ---
echo "[2/4] Compiling demo application..."
javac -d "${DEMO_OUT}" "${DEMO_SRC}"
echo "      Compiled: ${DEMO_SRC}"

# --- Step 3: Clear old PID-prefixed logs matching the base pattern ---
LOG_DIR="$(dirname "${LOG_FILE}")"
LOG_BASE="$(basename "${LOG_FILE}")"
echo "[3/4] Clearing previous log files matching: ${LOG_DIR}/*-${LOG_BASE}"
rm -f "${LOG_DIR}"/*-"${LOG_BASE}"

# --- Step 4: Run the demo ---
echo "[4/4] Running demo with agent attached..."
echo "      Base log path: ${LOG_FILE} (PID will be prefixed automatically)"
echo ""

java "-javaagent:${AGENT_JAR}=${LOG_FILE}" -cp "${DEMO_OUT}" SampleApp

# Find the PID-prefixed log file that was just created
ACTUAL_LOG=$(ls -t "${LOG_DIR}"/*-"${LOG_BASE}" 2>/dev/null | head -1)

echo ""
echo "============================================"
echo " Logged operations (${ACTUAL_LOG}):"
echo "============================================"
echo ""
cat "${ACTUAL_LOG}"
echo ""
echo "Total entries: $(wc -l < "${ACTUAL_LOG}")"
