#!/usr/bin/env bash
# Arabic guard (B4). The bundled Arabic font keeps lam-alef only under "liga". Android switches liga off when letter
# spacing is not 0, and forcing font features (init/medi/fina) breaks joining. So: no fontFeatureSettings anywhere,
# and letterSpacing only as 0 (or on a line marked "ar-ok" with a reason).
cd "$(dirname "$0")/.." || exit 1
bad=$(grep -rnE "fontFeatureSettings|setFontFeatureSettings|letterSpacing" app/src/main/java \
  | grep -vE "ar-ok|letterSpacing *= *0(\.0)?(\.sp|f)?[ ,)]*$|letterSpacing *= *0\.sp|^[^:]*:[0-9]+: *(\*|//)" || true)
if [ -n "$bad" ]; then
  echo "Arabic guard failed: letter spacing or font features can break lam-alef (الا):"; echo "$bad"; exit 1
fi
echo "Arabic guard OK"
