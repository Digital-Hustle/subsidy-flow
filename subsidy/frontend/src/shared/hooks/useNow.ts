import { useEffect, useState } from "react";

/** Текущее время, обновляется раз в stepMs. */
export const useNow = (stepMs = 1000) => {
  const [now, setNow] = useState(Date.now);
  useEffect(() => { const id = setInterval(() => setNow(Date.now()), stepMs); return () => clearInterval(id); }, [stepMs]);
  return now;
};
