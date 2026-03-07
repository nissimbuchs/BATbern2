import React, { useEffect, useState } from 'react';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/public/ui/sheet';
import { Button } from '@/components/public/ui/button';

interface DelaySessionSheetProps {
  open: boolean;
  onClose: () => void;
  onDelay: (minutes: number) => void;
  disabled?: boolean;
}

const DELAY_OPTIONS = [5, 10, 15, 20] as const;

export const DelaySessionSheet: React.FC<DelaySessionSheetProps> = ({
  open,
  onClose,
  onDelay,
  disabled = false,
}) => {
  const [sending, setSending] = useState(false);

  useEffect(() => {
    if (!open) setSending(false);
  }, [open]);

  const handleDelay = (minutes: number) => {
    setSending(true);
    onDelay(minutes);
    onClose();
  };

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="bottom" className="dark rounded-t-2xl pb-10 border-border">
        <SheetHeader className="mb-6">
          <SheetTitle className="text-center text-xl text-foreground">
            Vorherige Session mehr Zeit geben?
          </SheetTitle>
        </SheetHeader>
        <div className="flex flex-col gap-3 max-w-sm mx-auto">
          {DELAY_OPTIONS.map((minutes) => (
            <button
              key={minutes}
              disabled={disabled || sending}
              onClick={() => handleDelay(minutes)}
              className="w-full py-4 rounded-xl text-lg font-bold bg-amber-500 text-white hover:bg-amber-600 disabled:opacity-50 disabled:cursor-not-allowed transition-colors"
            >
              +{minutes} min
            </button>
          ))}
          <Button
            variant="ghost"
            onClick={onClose}
            className="w-full mt-1 text-muted-foreground hover:text-foreground"
          >
            Abbrechen
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
};
