// Route deviation detection using nearest point on each route segment.

function checkDeviationFromRoute(currentLat, currentLng, routePath) {
  if (!routePath || routePath.length < 2) return 0;

  let minDistance = Infinity;
  for (let i = 0; i < routePath.length - 1; i++) {
    const a = routePath[i];
    const b = routePath[i + 1];
    minDistance = Math.min(
      minDistance,
      distanceToSegmentKm(currentLat, currentLng, a[0], a[1], b[0], b[1]),
    );
  }
  return minDistance;
}

function distanceToSegmentKm(lat, lng, lat1, lng1, lat2, lng2) {
  const midLat = ((lat1 + lat2) / 2) * Math.PI / 180;
  const kmPerDegLat = 111.32;
  const kmPerDegLng = 111.32 * Math.cos(midLat);

  const px = lng * kmPerDegLng;
  const py = lat * kmPerDegLat;
  const ax = lng1 * kmPerDegLng;
  const ay = lat1 * kmPerDegLat;
  const bx = lng2 * kmPerDegLng;
  const by = lat2 * kmPerDegLat;

  const dx = bx - ax;
  const dy = by - ay;
  if (dx === 0 && dy === 0) return Math.hypot(px - ax, py - ay);

  const t = Math.max(0, Math.min(1, ((px - ax) * dx + (py - ay) * dy) / (dx * dx + dy * dy)));
  const cx = ax + t * dx;
  const cy = ay + t * dy;
  return Math.hypot(px - cx, py - cy);
}
