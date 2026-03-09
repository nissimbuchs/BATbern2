import React from 'react';
import { Sheet, SheetContent, SheetHeader, SheetTitle } from '@/components/public/ui/sheet';
import { Button } from '@/components/public/ui/button';

interface ExtendSessionSheetProps {
  open: boolean;
  onClose: () => void;
  onExtend: (minutes: number) => void;
}

const REDUCE_OPTIONS = [-20, -15, -10, -5] as const;
const EXTEND_OPTIONS = [5, 10, 15, 20] as const;

export const ExtendSessionSheet: React.FC<ExtendSessionSheetProps> = ({
  open,
  onClose,
  onExtend,
}) => {
  const handleAdjust = (minutes: number) => {
    onExtend(minutes);
    onClose();
  };

  return (
    <Sheet open={open} onOpenChange={(o) => !o && onClose()}>
      <SheetContent side="bottom" className="dark rounded-t-2xl pb-10 border-border">
        <SheetHeader className="mb-5">
          <SheetTitle className="text-center text-xl text-foreground">
            Session Zeit anpassen
          </SheetTitle>
        </SheetHeader>

        <div className="max-w-sm mx-auto space-y-4">
          {/* Reduce */}
          <div>
            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-widest mb-2 text-center">
              Kürzen
            </p>
            <div className="grid grid-cols-4 gap-2">
              {REDUCE_OPTIONS.map((minutes) => (
                <button
                  key={minutes}
                  onClick={() => handleAdjust(minutes)}
                  className="py-3 rounded-xl text-base font-bold border-2 border-amber-500 text-amber-400 hover:bg-amber-500/10 transition-colors"
                >
                  {minutes} min
                </button>
              ))}
            </div>
          </div>

          {/* Extend */}
          <div>
            <p className="text-xs font-semibold text-muted-foreground uppercase tracking-widest mb-2 text-center">
              Verlängern
            </p>
            <div className="grid grid-cols-4 gap-2">
              {EXTEND_OPTIONS.map((minutes) => (
                <button
                  key={minutes}
                  onClick={() => handleAdjust(minutes)}
                  className="py-3 rounded-xl text-base font-bold bg-primary text-primary-foreground hover:bg-primary/90 transition-colors"
                >
                  +{minutes} min
                </button>
              ))}
            </div>
          </div>

          <Button
            variant="ghost"
            onClick={onClose}
            className="w-full text-muted-foreground hover:text-foreground"
          >
            Abbrechen
          </Button>
        </div>
      </SheetContent>
    </Sheet>
  );
};
