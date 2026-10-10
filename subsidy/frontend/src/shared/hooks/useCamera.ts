import { useCallback, useEffect, useRef, useState } from "react";

export type CameraState = "idle" | "starting" | "on" | "denied" | "unavailable";

/** Камера с авто-остановкой при размонтировании. facing: "user" — фронтальная, "environment" — тыльная. */
export const useCamera = (facing: "user" | "environment" = "user") => {
  const videoRef = useRef<HTMLVideoElement | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const [state, setState] = useState<CameraState>("idle");

  const stop = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    setState("idle");
  }, []);

  const start = useCallback(async () => {
    if (!navigator.mediaDevices?.getUserMedia) { setState("unavailable"); return; }
    setState("starting");
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: facing, width: { ideal: 1280 }, height: { ideal: 720 } }, audio: false });
      streamRef.current = stream;
      if (videoRef.current) { videoRef.current.srcObject = stream; await videoRef.current.play().catch(() => undefined); }
      setState("on");
    } catch (e) {
      setState(e instanceof DOMException && e.name === "NotAllowedError" ? "denied" : "unavailable");
    }
  }, [facing]);

  useEffect(() => () => streamRef.current?.getTracks().forEach((t) => t.stop()), []);

  /** Снимок кадра в dataURL (JPEG), уменьшенный до maxSide. */
  const snapshot = useCallback((maxSide = 320) => {
    const v = videoRef.current;
    if (!v || !v.videoWidth) return null;
    const k = maxSide / Math.max(v.videoWidth, v.videoHeight);
    const c = document.createElement("canvas");
    c.width = Math.round(v.videoWidth * k); c.height = Math.round(v.videoHeight * k);
    c.getContext("2d")!.drawImage(v, 0, 0, c.width, c.height);
    return c.toDataURL("image/jpeg", 0.8);
  }, []);

  return { videoRef, state, start, stop, snapshot };
};
