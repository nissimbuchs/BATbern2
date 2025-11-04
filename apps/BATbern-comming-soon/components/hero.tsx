import { Button } from "@/components/ui/button";
import Link from "next/link";
import Script from "next/script";

export function Hero() {
  return (
    <section className="relative h-screen w-full overflow-hidden">
      {/* Unicorn.studio Interactive Background */}
      <div
        className="absolute inset-0 z-0 w-full h-full"
        aria-hidden="true"
      >
        <div
          data-us-project="jfzsiwProJi81qvb7uKX"
          style={{ width: '100%', height: '100%' }}
        />
      </div>

      {/* Unicorn.studio Script */}
      <Script id="unicorn-studio-init" strategy="afterInteractive">
        {`!function(){if(!window.UnicornStudio){window.UnicornStudio={isInitialized:!1};var i=document.createElement("script");i.src="https://cdn.jsdelivr.net/gh/hiunicornstudio/unicornstudio.js@v1.4.34/dist/unicornStudio.umd.js",i.onload=function(){window.UnicornStudio.isInitialized||(UnicornStudio.init(),window.UnicornStudio.isInitialized=!0)},(document.head || document.body).appendChild(i)}}();`}
      </Script>

      {/* Content */}
      <div className="relative z-10 container mx-auto px-4 sm:px-6 lg:px-8 h-full flex items-end pb-16 md:pb-20 lg:pb-24">
        <div className="max-w-4xl">
          <h1 className="text-4xl sm:text-5xl md:text-6xl lg:text-7xl xl:text-8xl font-bold text-foreground leading-tight mb-8">
            Coming soon...
          </h1>
          {/* <Button size="lg" className="text-base md:text-lg" asChild>
            <Link href="/register">Register</Link>
          </Button> */}
        </div>
      </div>
    </section>
  );
}
