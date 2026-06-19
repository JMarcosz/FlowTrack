export type ServiceMode = "web" | "webhook" | "worker";

export function currentServiceMode(): ServiceMode {
  const value = (process.env.SERVICE_MODE ?? "web").toLowerCase();
  if (value === "webhook" || value === "worker") return value;
  return "web";
}

export function isModeAllowed(...allowed: ServiceMode[]): boolean {
  return allowed.includes(currentServiceMode());
}
