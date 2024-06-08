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

  public onlineUsers: User[] = [];

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
      this.loadOnlineUsers();
      this.subscribeToUserStatusChanges();
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
    this.showScreen = true;
  }

  loadOnlineUsers(): void {
    this.chatService.getOnlineUsers().then(users => {
      this.onlineUsers = users;
      this.onlineUsers.forEach(user => {
        user.image = `assets/user/${this.getRandomImageNumber()}.png`;
      });
    }).catch(error => {
      console.error('Failed to load online users:', error);
    });
  }

  getRandomImageNumber(): number {
    return Math.floor(Math.random() * 11) + 1;
  }

  subscribeToUserStatusChanges(): void {
    this.chatService.subscribeToUserStatusChanges().subscribe(
      (users) => {
        this.onlineUsers = users;
        this.onlineUsers.map(x => x.image = `assets/user/${this.getRandomImageNumber()}.png`)
      },
      (error) => {
        console.error('Failed to subscribe to user status changes:', error);
      }
    );
  }

  selectUserHandler(login: string): void {
    const user = this.onlineUsers.find(user => user.login === login);
    if (user && this.currentUser) {
      this.selectedUser = user;
      this.roomId = this.getRoomId(this.currentUser.login, user.login);
      // Load previous messages if any
      this.messageArray.set(this.roomId, this.messageArray.get(this.roomId) || []);

      this.join(this.currentUser.login, this.roomId);
    }
  }

  getRoomId(userId: string, selectedUserId: string): string {
    const sortedIds = [userId, selectedUserId].sort((a, b) => a.localeCompare(b));
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
        user: this.currentUser.login,
        room: this.roomId,
        message: this.messageText,
        date: timestamp
      };

      const token = localStorage.getItem('token');
      if (token) {
        this.chatService.sendMessage({ ...messageData, token: token });

        // Update local message array to display sent message immediately
        const messages = this.messageArray.get(this.roomId) || [];
        messages.push({
          userName: this.currentUser.login,
          message: this.messageText,
          timestamp: timestamp
        });
        this.messageArray.set(this.roomId, messages);

        this.messageText = '';
      }
    }
  }
}
