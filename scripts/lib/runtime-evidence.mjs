// HotSpot PrintCompilation begins successful records with elapsed time and a
// numeric compilation ID. Configuration and exclusion diagnostics do not.
export function hasMixCompilation(log) {
  return /^\s*\d+\s+\d+\s+(?:[%!bsn]\s*)*(?:\d\s+)?dev\.javafornestjs\.runtime\.RuntimeLab::mix\s+\(\d+ bytes\)(?![^\r\n]*(?:made not|excluded))/m.test(
    log,
  )
}
