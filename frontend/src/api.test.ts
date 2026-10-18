import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { api, ApiError } from "./api";

const fetchMock = vi.fn();

beforeEach(() => {
  fetchMock.mockReset();
  vi.stubGlobal("fetch", fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("api", () => {
  it("parses JSON response on success", async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ id: "e1", name: "Kim" }), { status: 200 }));
    const r = await api<{ id: string }>("GET", "/api/v1/employees");
    expect(r.id).toBe("e1");
    expect(fetchMock).toHaveBeenCalledWith("/api/v1/employees", { method: "GET" });
  });

  it("sends JSON body and content-type when body provided", async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await api("PATCH", "/api/v1/employees/1", { customInstructions: "x" });
    const [, opts] = fetchMock.mock.calls[0];
    expect(opts.method).toBe("PATCH");
    expect(opts.headers).toEqual({ "Content-Type": "application/json" });
    expect(opts.body).toBe(JSON.stringify({ customInstructions: "x" }));
  });

  it("throws ApiError with error field and status on failure", async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ error: "아카이브된 고객은 연결할 수 없습니다." }), { status: 409 })
    );
    let caught: unknown = null;
    try {
      await api("POST", "/api/v1/projects", {});
    } catch (e) {
      caught = e;
    }
    expect(caught).toBeInstanceOf(ApiError);
    expect((caught as ApiError).status).toBe(409);
    expect((caught as ApiError).message).toBe("아카이브된 고객은 연결할 수 없습니다.");
  });

  it("falls back to message/detail fields and HTTP status", async () => {
    fetchMock.mockResolvedValueOnce(new Response(JSON.stringify({ detail: "nope" }), { status: 400 }));
    await expect(api("GET", "/x")).rejects.toMatchObject({ message: "nope", status: 400 });

    fetchMock.mockResolvedValueOnce(new Response("", { status: 500 }));
    await expect(api("GET", "/x")).rejects.toMatchObject({ message: "HTTP 500", status: 500 });
  });

  it("returns text body when response is not JSON", async () => {
    fetchMock.mockResolvedValueOnce(new Response("plain", { status: 200 }));
    const r = await api<string>("GET", "/x");
    expect(r).toBe("plain");
  });
});
