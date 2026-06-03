import { nextTick, onBeforeUnmount, onMounted, watch } from 'vue'

const FOCUSABLE_SELECTOR = [
  'a[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  '[tabindex]:not([tabindex="-1"])'
].join(',')

export const useModalDialog = ({
  isOpen,
  dialogRef,
  onClose,
  initialFocusRef
}) => {
  let previousActiveElement = null
  let listening = false

  const getFocusableElements = () => {
    if (!dialogRef.value) {
      return []
    }

    return Array.from(dialogRef.value.querySelectorAll(FOCUSABLE_SELECTOR))
      .filter((element) => !element.hasAttribute('disabled'))
  }

  const focusDialog = async () => {
    previousActiveElement = document.activeElement instanceof HTMLElement
      ? document.activeElement
      : null

    await nextTick()

    const fallbackTarget = getFocusableElements()[0] || dialogRef.value
    const target = initialFocusRef?.value || fallbackTarget
    target?.focus()
  }

  const restoreFocus = () => {
    previousActiveElement?.focus?.()
    previousActiveElement = null
  }

  const handleKeydown = (event) => {
    if (!isOpen.value) {
      return
    }

    if (event.key === 'Escape') {
      event.preventDefault()
      onClose()
      return
    }

    if (event.key !== 'Tab') {
      return
    }

    const focusableElements = getFocusableElements()
    if (focusableElements.length === 0) {
      event.preventDefault()
      dialogRef.value?.focus()
      return
    }

    const firstElement = focusableElements[0]
    const lastElement = focusableElements[focusableElements.length - 1]

    if (event.shiftKey && document.activeElement === firstElement) {
      event.preventDefault()
      lastElement.focus()
    } else if (!event.shiftKey && document.activeElement === lastElement) {
      event.preventDefault()
      firstElement.focus()
    }
  }

  const activate = () => {
    if (listening) {
      return
    }

    listening = true
    document.addEventListener('keydown', handleKeydown)
    focusDialog()
  }

  const deactivate = () => {
    if (!listening) {
      return
    }

    listening = false
    document.removeEventListener('keydown', handleKeydown)
    restoreFocus()
  }

  watch(isOpen, (open) => {
    if (open) {
      activate()
    } else {
      deactivate()
    }
  }, { flush: 'post' })

  onMounted(() => {
    if (isOpen.value) {
      activate()
    }
  })

  onBeforeUnmount(deactivate)
}
