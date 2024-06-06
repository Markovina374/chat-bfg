import { AfterViewInit, Component, OnInit, ViewChild } from '@angular/core';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { ChatService } from './services/chat/chat.service';
import { Message, MessageResponse, User } from "./models";

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrls: ['./app.component.scss']
})
export class AppComponent implements OnInit, AfterViewInit {

  @ViewChild('popup', { static: false }) popup: any;

  public roomId?: string;
  public messageText?: string;
  public messageArray: Map<string, Message[]> = new Map<string, Message[]>();

  public showScreen = false;
  public phone?: string;
  public currentUser?: User;
  public selectedUser?: User;

  public userList: User[] = [
    {
      id: 1,
      name: 'The Swag Coder',
      phone: '2',
      image: 'assets/user/user-1.png',
    },
    {
      id: 2,
      name: 'Wade Warren',
      phone: '1',
      image: 'assets/user/user-2.png',
    },
    {
      id: 3,
      name: 'Albert Flores',
      phone: '3',
      image: 'assets/user/user-3.png',
    },
    {
      id: 4,
      name: 'Dianne Russell',
      phone: '4',
      image: 'assets/user/user-4.png',
    }
  ];

  constructor(
    private modalService: NgbModal,
    private chatService: ChatService
  ) { }

  ngOnInit(): void {
    this.chatService.getMessage()
      .subscribe((data: { user: string, room: string, message: string, timestamp: string }) => {
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
        console.log(this.messageArray)
      });
  }

  ngAfterViewInit(): void {
    this.openPopup(this.popup);
  }

  openPopup(content: any): void {
    this.modalService.open(content, { backdrop: 'static', centered: true });
  }

  login(dismiss: any): void {
    if (this.phone) {
      this.currentUser = this.userList.find(user => user.phone === this.phone.toString());
      this.userList = this.userList.filter(user => user.phone !== this.phone.toString());

      if (this.currentUser) {
        this.showScreen = true;
        dismiss();
      }
    }
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
    // Объединяем их в строку с разделителем
    return `room-${sortedIds.join('-')}`;
  }

  join(username: string, roomId: string): void {
    this.chatService.joinRoom({ user: username, room: roomId });
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

      this.chatService.sendMessage(messageData);

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
