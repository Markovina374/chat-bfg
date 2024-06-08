import { Component, OnInit } from '@angular/core';
import { Router } from '@angular/router';
import { ChatService } from './services/chat/chat.service';
import { Message, MessageResponse, User } from "./models";

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit {
  public roomId?: string;
  public messageText?: string;
  public messageArray: Map<string, Message[]> = new Map<string, Message[]>();

  public showScreen = false;
  public currentUser?: User;
  public selectedUser?: User;

  public userList: User[] = [];

  constructor(
    private chatService: ChatService,
    private router: Router
  ) { }

  ngOnInit(): void {
    const token = localStorage.getItem('token');
    if (!token) {
      this.router.navigate(['/login']);
    } else {
      // Fetch user details and initialize the chat
      this.initializeChat();
    }
  }

  private initializeChat(): void {
    this.chatService.getMessage().subscribe((data: { user: string, room: string, message: string, timestamp: string }) => {
      const messages = this.messageArray.get(data.room) || [];
      const isDuplicate = messages.some(message =>
        message.message === data.message &&
        message.userName === data.user &&
        message.timestamp === data.timestamp
      );
      if (!isDuplicate) {
        messages.push({
          userName: data.user,
          message: data.message,
          timestamp: data.timestamp
        });
        this.messageArray.set(data.room, messages);
      }
      console.log(this.messageArray);

    });

    // Fetch user and user list from server or local storage
    this.currentUser = JSON.parse(localStorage.getItem('currentUser') || '{}');
    this.loadAllUsers();
    this.showScreen = true;
  }

  loadAllUsers(): void {
    this.chatService.getAllUsers().subscribe(
      (data) => {
        this.userList = data.users;
        console.log('Users:', this.userList);
      },
      (error) => {
        console.error('Failed to load users:', error);
      }
    );
  }

  selectUserHandler(phone: string): void {
    const user = this.userList.find(user => user.phone === phone);
    if (user && this.currentUser) {
      this.selectedUser = user;
      this.roomId = this.getRoomId(this.currentUser.id, user.id);
      // Load previous messages if any
      this.messageArray.set(this.roomId, this.messageArray.get(this.roomId) || []);

      this.join(this.currentUser.name, this.roomId);
    }
  }

  getRoomId(userId: number, selectedUserId: number): string {
    const sortedIds = [userId, selectedUserId].sort((a, b) => a - b);
    return `room-${sortedIds.join('-')}`;
  }

  join(username: string, roomId: string): void {
    const token = localStorage.getItem('token');
    if (token) {
      this.chatService.joinRoom({ user: username, room: roomId, token: token });
    }
  }

  sendMessage(): void {
    if (this.messageText && this.currentUser && this.roomId) {
      const timestamp = new Date().toISOString();
      const messageData: MessageResponse = {
        user: this.currentUser.name,
        room: this.roomId,
        message: this.messageText,
        timestamp: timestamp
      };

      const token = localStorage.getItem('token');
      if (token) {
        this.chatService.sendMessage({ ...messageData, token: token });

        // Update local message array to display sent message immediately
        const messages = this.messageArray.get(this.roomId) || [];
        messages.push({
          userName: this.currentUser.name,
          message: this.messageText,
          timestamp: timestamp
        });
        this.messageArray.set(this.roomId, messages);

        this.messageText = '';
      }
    }
  }
}
