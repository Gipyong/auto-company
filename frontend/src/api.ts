export class ApiError extends Error {
  status: number;

  constructor(message: string, status: number) {
    super(message);
    this.status = status;
  }
}

export async function api<T = any>(method: string, path: string, body?: unknown): Promise<T> {
  const opts: RequestInit = { method };
  if (body !== undefined) {
    opts.headers = { "Content-Type": "application/json" };
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(path, opts);
  const text = await res.text();
  let data: unknown = null;
  if (text) {
    try {
      data = JSON.parse(text);
    } catch {
      data = text;
    }
  }
  if (!res.ok) {
    const detail =
      data && typeof data === "object"
        ? (data as any).error || (data as any).message || (data as any).detail
        : data;
    throw new ApiError(detail || `HTTP ${res.status}`, res.status);
  }
  return data as T;
}

export async function apiNoContent(method: string, path: string, body?: unknown): Promise<void> {
  await api(method, path, body);
}
