export interface User {
  login: string;
  image: string;
}

export interface Message {
  login: string;
  message: string;
  date: string;
}

export interface MessageResponse {
  user: string;
  room: string;
  message: string;
  date: string;
}
