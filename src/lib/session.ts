type AuthBridge = {
  getAccessToken: () => string | null;
  refresh: () => Promise<boolean>;
};

let bridge: AuthBridge = {
  getAccessToken: () => null,
  refresh: async () => false,
};

export function configureAuthBridge(next: AuthBridge) {
  bridge = next;
}

export function getAccessToken() {
  return bridge.getAccessToken();
}

export function refreshAuthentication() {
  return bridge.refresh();
}
