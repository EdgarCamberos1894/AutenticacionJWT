/// <reference types="vite/client" />

declare global {
  interface Crypto {
    randomUUID(): string;
  }
}

export {};
