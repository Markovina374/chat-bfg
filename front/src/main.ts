import { platformBrowserDynamic } from '@angular/platform-browser-dynamic';
import { AppModule } from './app/app.module';  // Убедитесь, что путь правильный

platformBrowserDynamic().bootstrapModule(AppModule)
  .catch(err => console.error(err));
