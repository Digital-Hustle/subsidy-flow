import { forwardRef } from "react";
import { motion, type HTMLMotionProps } from "motion/react";
import { cva, type VariantProps } from "class-variance-authority";
import { cn } from "@/shared/lib";
import { press } from "@/shared/config/motion";

export const buttonVariants = cva(
  "relative inline-flex min-w-0 select-none items-center justify-center gap-2 whitespace-nowrap font-medium outline-none transition-colors duration-fast focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 focus-visible:ring-offset-background disabled:pointer-events-none disabled:opacity-45 [&_svg]:shrink-0",
  {
    variants: {
      variant: {
        primary: "bg-primary text-primary-foreground hover:bg-primary-hover",
        /** Как «СберБанк Онлайн» в шапке Сбера: градиент, белый полужирный текст */
        brand: "bg-brand-deep font-semibold text-white shadow-glow hover:brightness-105",
        secondary: "bg-surface text-surface-foreground hover:bg-surface-hover",
        outline: "border border-border-strong bg-card text-foreground hover:bg-muted",
        ghost: "text-foreground hover:bg-surface",
        quiet: "text-muted-foreground hover:bg-surface hover:text-foreground",
        danger: "bg-danger text-danger-foreground hover:opacity-90",
        "danger-soft": "bg-danger-soft text-danger-soft-foreground hover:opacity-85",
        accent: "bg-accent text-accent-foreground hover:bg-success-soft",
        inverse: "bg-inverse text-inverse-foreground hover:opacity-90",
      },
      size: {
        sm: "h-control-sm rounded-sm px-3.5 text-sm [&_svg]:size-4",
        md: "h-control-md rounded-md px-4 text-sm sm:px-5 [&_svg]:size-5",
        lg: "h-control-lg rounded-md px-6 text-base [&_svg]:size-5",
        xl: "h-control-xl rounded-lg px-8 text-lg [&_svg]:size-6",
        "icon-sm": "size-control-sm rounded-sm [&_svg]:size-4",
        icon: "size-control-md rounded-md [&_svg]:size-5",
        "icon-lg": "size-control-lg rounded-md [&_svg]:size-6",
      },
      block: { true: "w-full" },
    },
    defaultVariants: { variant: "primary", size: "md" },
  },
);

export type ButtonProps = Omit<HTMLMotionProps<"button">, "children"> & VariantProps<typeof buttonVariants> & { children?: React.ReactNode };

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(({ className, variant, size, block, type = "button", ...props }, ref) => (
  <motion.button ref={ref} type={type} className={cn(buttonVariants({ variant, size, block }), className)} {...press} {...props} />
));
Button.displayName = "Button";
