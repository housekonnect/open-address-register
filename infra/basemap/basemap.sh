#!/usr/bin/env sh
# Fills the Docker volume `ugaddress-basemap` with the self-hosted basemap and verifies every file (pins.env):
#   /basemap/basemap.pmtiles   Protomaps extract of OpenStreetMap, served by Martin as the source `basemap`
#   /basemap/assets/fonts      glyphs (PBF) of the font stacks the style uses, served by the portal
#   /basemap/assets/sprites/v4 sprite sheets, served by the portal
#
# BASEMAP_AREA selects the extract: `uganda` (default) or `demo` (only the synthetic district, used by CI).
# Files that already match their pinned SHA-256 are kept, so running this again is cheap.
set -eu

here="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=pins.env
. "$here/pins.env"

area="${BASEMAP_AREA:-uganda}"
case "$area" in
  uganda) bbox="$AREA_UGANDA_BBOX"; expected="$AREA_UGANDA_SHA256" ;;
  demo) bbox="$AREA_DEMO_BBOX"; expected="$AREA_DEMO_SHA256" ;;
  *) echo "Unknown BASEMAP_AREA '$area' (use 'uganda' or 'demo')." >&2; exit 2 ;;
esac

volume=ugaddress-basemap
docker volume create "$volume" >/dev/null

# Runs a shell command in the helper image with the volume mounted at /basemap.
helper() {
  docker run --rm -v "$volume:/basemap" -e ASSETS_COMMIT="$ASSETS_COMMIT" -e DIR="${2:-}" \
    --entrypoint sh "$HELPER_IMAGE" -c "$1"
}

# SHA-256 over the content of the kept asset files below /basemap/<dir> (empty if the directory is missing).
assets_sum() {
  helper 'cd "/basemap/$DIR" 2>/dev/null &&
    find "fonts/Noto Sans Regular" "fonts/Noto Sans Medium" "fonts/Noto Sans Italic" fonts/OFL.txt sprites/v4 \
      -type f -exec sha256sum {} + | LC_ALL=C sort -k2 | sha256sum | cut -d" " -f1' "$1"
}

# --- Glyphs and sprites ---------------------------------------------------------------------------------------------
if [ "$(assets_sum assets)" = "$ASSETS_SHA256" ]; then
  echo "Basemap glyphs and sprites: verified, keeping them."
else
  echo "Basemap glyphs and sprites: downloading protomaps/basemaps-assets@$ASSETS_COMMIT ..."
  helper 'set -e
    rm -rf /basemap/assets.tmp && mkdir -p /basemap/assets.tmp && cd /basemap/assets.tmp
    wget -qO- "https://codeload.github.com/protomaps/basemaps-assets/tar.gz/$ASSETS_COMMIT" | tar -xz --strip-components=1
    find fonts -mindepth 1 -maxdepth 1 ! -name "Noto Sans Regular" ! -name "Noto Sans Medium" ! -name "Noto Sans Italic" \
      ! -name OFL.txt -exec rm -rf {} +
    find sprites -mindepth 1 -maxdepth 1 ! -name v4 -exec rm -rf {} +
    find . -mindepth 1 -maxdepth 1 ! -name fonts ! -name sprites -exec rm -rf {} +'
  actual="$(assets_sum assets.tmp)"
  if [ "$actual" != "$ASSETS_SHA256" ]; then
    helper 'rm -rf /basemap/assets.tmp'
    echo "Checksum mismatch for basemap assets: expected $ASSETS_SHA256, got $actual." >&2
    exit 1
  fi
  helper 'rm -rf /basemap/assets && mv /basemap/assets.tmp /basemap/assets'
  echo "Basemap glyphs and sprites: verified."
fi

# --- PMTiles extract ------------------------------------------------------------------------------------------------
if [ "$(helper 'sha256sum /basemap/basemap.pmtiles 2>/dev/null | cut -d" " -f1')" = "$expected" ]; then
  echo "Basemap tiles ($area): verified, keeping them."
  exit 0
fi

echo "Basemap tiles ($area): extracting bbox $bbox up to z$BASEMAP_MAXZOOM from Protomaps build $BASEMAP_BUILD ..."
docker run --rm -v "$volume:/basemap" "$PMTILES_IMAGE" extract \
  "https://build.protomaps.com/$BASEMAP_BUILD.pmtiles" /basemap/basemap.pmtiles.tmp \
  --bbox="$bbox" --maxzoom="$BASEMAP_MAXZOOM" --download-threads=8
actual="$(helper 'sha256sum /basemap/basemap.pmtiles.tmp | cut -d" " -f1')"
if [ "$actual" != "$expected" ]; then
  helper 'rm -f /basemap/basemap.pmtiles.tmp'
  echo "Checksum mismatch for the $area extract of build $BASEMAP_BUILD: expected $expected, got $actual." >&2
  echo "If you changed BASEMAP_BUILD on purpose, pin the new value in infra/basemap/pins.env." >&2
  exit 1
fi
helper 'mv /basemap/basemap.pmtiles.tmp /basemap/basemap.pmtiles'
echo "Basemap tiles ($area): verified."

# Martin opens the file at start-up; restart it if the stack is running.
martin="$(docker ps -q --filter label=com.docker.compose.project=ugaddress --filter label=com.docker.compose.service=martin)"
if [ -n "$martin" ]; then
  docker restart "$martin" >/dev/null
  echo "Restarted Martin to pick up the new extract."
fi
