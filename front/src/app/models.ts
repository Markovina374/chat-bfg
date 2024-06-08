export interface User {
  login: string;
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
  date: string;
}
