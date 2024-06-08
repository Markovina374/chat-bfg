export interface User {
  id: number;
  name: string;
  phone: string;
  image: string;
}

export interface Message {
  userName: string;
  message: string;
  timestamp: string;
}

export interface MessageResponse {
  user: string;
  room: string;
  message: string;
  timestamp: string;
}

export interface AuthResponse {
  token: string;
  user: User;
}

export interface AuthRequest {
  event: string;
  data: {
    phone: string;
    password: string;
    name?: string;
  };
}
