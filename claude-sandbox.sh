#!/bin/bash
set -e

IMAGE="claude-android-sandbox"
CONTAINER_NAME="claude-sandbox"
PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"

# Build image if it doesn't exist
if ! podman image exists "$IMAGE"; then
    echo "Building sandbox image (this takes a few minutes)..."
    podman build -t "$IMAGE" "$PROJECT_DIR"
fi

# Start host adb server listening on all interfaces so the container can reach it over TCP
adb kill-server 2>/dev/null || true
adb -a nodaemon server 2>/dev/null &
sleep 1

# Resume existing container if stopped, otherwise create a new one
if podman container exists "$CONTAINER_NAME"; then
    echo "Resuming sandbox..."
    podman start -ai "$CONTAINER_NAME"
else
    echo "Starting Claude in sandbox..."
    podman run -it \
        --name "$CONTAINER_NAME" \
        --network=host \
        -v "$PROJECT_DIR:/workspace:Z" \
        -v "$HOME/.claude:/root/.claude:Z" \
        "$IMAGE" \
        claude
fi
