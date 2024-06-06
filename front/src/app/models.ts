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
  user: any;
  room: string;
  message: string;
  timestamp: string;
}


export interface StorageItem {
  roomId: string;
  chats: Message[];
}


