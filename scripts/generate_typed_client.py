#!/usr/bin/env python3
"""Generate a lightweight typed TypeScript client from OpenAPI JSON."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any


ROOT = Path(__file__).resolve().parents[1]
SPEC_PATH = ROOT / "docs" / "openapi" / "stock-ai.openapi.json"
OUT_PATH = ROOT / "frontend" / "src" / "api" / "generated" / "client.ts"


def _safe_name(value: str) -> str:
    value = re.sub(r"[^a-zA-Z0-9]+", "_", value).strip("_")
    if not value:
        value = "unnamed"
    if value[0].isdigit():
        value = f"n_{value}"
    return value


def _method_name(http_method: str, path: str) -> str:
    parts = [_safe_name(p.replace("{", "").replace("}", "")) for p in path.split("/") if p]
    joined = "_".join(parts)
    return _safe_name(f"{http_method.lower()}_{joined}")


def _extract_ref_name(ref: str) -> str:
    return ref.split("/")[-1]


def _ts_type(schema: dict[str, Any] | None, components: dict[str, Any]) -> str:
    if not schema:
        return "unknown"

    if "$ref" in schema:
        return _extract_ref_name(schema["$ref"])

    if "enum" in schema and isinstance(schema["enum"], list):
        values = []
        for v in schema["enum"]:
            if isinstance(v, str):
                values.append(f"'{v}'")
            elif isinstance(v, bool):
                values.append("true" if v else "false")
            else:
                values.append(str(v))
        return " | ".join(values) if values else "unknown"

    t = schema.get("type")

    if t == "string":
        return "string"
    if t in {"number", "integer"}:
        return "number"
    if t == "boolean":
        return "boolean"
    if t == "array":
        return f"{_ts_type(schema.get('items', {}), components)}[]"
    if t == "object" or "properties" in schema:
        props = schema.get("properties", {})
        required = set(schema.get("required", []))
        additional = schema.get("additionalProperties")

        fields: list[str] = []
        for key, value in props.items():
            optional = "?" if key not in required else ""
            fields.append(f"{key}{optional}: {_ts_type(value, components)}")

        if additional is True:
            fields.append("[key: string]: unknown")
        elif isinstance(additional, dict):
            fields.append(f"[key: string]: {_ts_type(additional, components)}")

        if not fields:
            return "Record<string, unknown>"
        return "{ " + "; ".join(fields) + " }"

    if "oneOf" in schema and isinstance(schema["oneOf"], list):
        return " | ".join(_ts_type(s, components) for s in schema["oneOf"])
    if "anyOf" in schema and isinstance(schema["anyOf"], list):
        return " | ".join(_ts_type(s, components) for s in schema["anyOf"])

    return "unknown"


def _success_schema(operation: dict[str, Any]) -> dict[str, Any] | None:
    responses = operation.get("responses", {})
    for code, body in responses.items():
        if not str(code).startswith("2"):
            continue
        content = body.get("content", {})
        app_json = content.get("application/json")
        if app_json and "schema" in app_json:
            return app_json["schema"]
    return None


def main() -> int:
    spec = json.loads(SPEC_PATH.read_text(encoding="utf-8"))
    components = spec.get("components", {}).get("schemas", {})
    paths = spec.get("paths", {})

    out: list[str] = []
    out.append("/* eslint-disable */")
    out.append("// AUTO-GENERATED FILE. DO NOT EDIT MANUALLY.")
    out.append("// Source: docs/openapi/stock-ai.openapi.json")
    out.append("")

    out.append("export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'DELETE' | 'PATCH';")
    out.append("")
    out.append("export interface ApiClientConfig {")
    out.append("  baseUrl: string;")
    out.append("  getAccessToken?: () => string | null;")
    out.append("}")
    out.append("")

    for name, schema in components.items():
        out.append(f"export type {name} = {_ts_type(schema, components)};")
    out.append("")

    out.append("function encodeQuery(query?: Record<string, unknown>): string {")
    out.append("  if (!query) return '';")
    out.append("  const params = new URLSearchParams();")
    out.append("  for (const [k, v] of Object.entries(query)) {")
    out.append("    if (v === undefined || v === null) continue;")
    out.append("    params.append(k, String(v));")
    out.append("  }")
    out.append("  const s = params.toString();")
    out.append("  return s ? `?${s}` : '';")
    out.append("}")
    out.append("")

    out.append("export class StockAiApiClient {")
    out.append("  private readonly baseUrl: string;")
    out.append("  private readonly getAccessToken?: () => string | null;")
    out.append("")
    out.append("  constructor(config: ApiClientConfig) {")
    out.append("    this.baseUrl = config.baseUrl.replace(/\\/$/, '');")
    out.append("    this.getAccessToken = config.getAccessToken;")
    out.append("  }")
    out.append("")
    out.append(
        "  private async request<T>(method: HttpMethod, path: string, options: { query?: Record<string, unknown>; body?: unknown; auth?: boolean; init?: RequestInit } = {}): Promise<T> {"
    )
    out.append("    const headers: Record<string, string> = { Accept: 'application/json' };")
    out.append("    if (options.body !== undefined) headers['Content-Type'] = 'application/json';")
    out.append("    if (options.auth && this.getAccessToken) {")
    out.append("      const token = this.getAccessToken();")
    out.append("      if (token) headers.Authorization = `Bearer ${token}`;")
    out.append("    }")
    out.append("    const res = await fetch(`${this.baseUrl}${path}${encodeQuery(options.query)}`, {")
    out.append("      ...(options.init ?? {}),")
    out.append("      method,")
    out.append("      headers,")
    out.append("      body: options.body !== undefined ? JSON.stringify(options.body) : undefined")
    out.append("    });")
    out.append("    if (!res.ok) {")
    out.append("      const text = await res.text();")
    out.append("      throw new Error(`${method} ${path} failed: ${res.status} ${text}`);")
    out.append("    }")
    out.append("    const text = await res.text();")
    out.append("    return (text ? JSON.parse(text) : {}) as T;")
    out.append("  }")
    out.append("")

    for path, path_item in paths.items():
        if not isinstance(path_item, dict):
            continue

        for http_method in ("get", "post", "put", "delete", "patch"):
            operation = path_item.get(http_method)
            if not operation:
                continue

            params = []
            query_params = []
            path_params = []
            for p in operation.get("parameters", []):
                if p.get("in") == "query":
                    query_params.append(p)
                elif p.get("in") == "path":
                    path_params.append(p)

            has_body = "requestBody" in operation
            response_schema = _success_schema(operation)
            return_type = _ts_type(response_schema, components)

            fn_name = _method_name(http_method, path)
            args: list[str] = []
            if path_params:
                parts = []
                for p in path_params:
                    name = _safe_name(p["name"])
                    ts = _ts_type(p.get("schema", {}), components)
                    parts.append(f"{name}: {ts}")
                args.append(f"path: {{ {', '.join(parts)} }}")
            if query_params:
                parts = []
                for p in query_params:
                    name = _safe_name(p["name"])
                    ts = _ts_type(p.get("schema", {}), components)
                    required = p.get("required", False)
                    opt = "" if required else "?"
                    parts.append(f"{name}{opt}: {ts}")
                args.append(f"query?: {{ {', '.join(parts)} }}")
            if has_body:
                req = operation["requestBody"].get("content", {}).get("application/json", {})
                body_schema = req.get("schema", {})
                body_type = _ts_type(body_schema, components)
                args.append(f"body: {body_type}")

            security = operation.get("security", [])
            auth_flag = "true" if security else "false"

            path_expr = path
            for p in path_params:
                raw_name = p["name"]
                name = _safe_name(raw_name)
                path_expr = path_expr.replace("{" + raw_name + "}", f"${{path.{name}}}")
            path_expr = f"`{path_expr}`"

            args_joined = ", ".join(args)
            if args_joined:
                args_joined += ", "
            out.append(
                f"  async {fn_name}({args_joined}init?: RequestInit): Promise<{return_type}> {{"
            )
            query_expr = "query" if query_params else "undefined"
            body_expr = "body" if has_body else "undefined"
            out.append(
                f"    return this.request<{return_type}>('{http_method.upper()}', {path_expr}, {{ query: {query_expr}, body: {body_expr}, auth: {auth_flag}, init }});"
            )
            out.append("  }")
            out.append("")

    out.append("}")
    out.append("")

    OUT_PATH.parent.mkdir(parents=True, exist_ok=True)
    OUT_PATH.write_text("\n".join(out), encoding="utf-8")
    print(f"Generated: {OUT_PATH}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
