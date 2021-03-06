(ns avi.editor
  "Functions (including basse responders, middleware, and utilties) for
   manipulating the editor map."
  (:require [packthread.core :refer :all]
            [avi.pervasive :refer :all]
            [avi.buffer :as b]))

;; -- Initial state ----------------------------------------------------------

(defn initial-editor
  [[lines columns] [filename]]
  {:mode :normal
   :buffer (b/open filename (- lines 2))
   :viewport {:size [lines columns]}
   :count nil
   :beep? false})

;; -- Building middlewares ---------------------------------------------------

(defn keystroke-middleware
  [keystroke a-fn]
  (fn [handler]
    (fn [editor event]
      (if (= event [:keystroke keystroke])
        (a-fn editor)
        (handler editor event)))))

;; -- Beeping ----------------------------------------------------------------

(defn beep
  [editor]
  (assoc editor :beep? true))

(defn wrap-reset-beep
  [handler]
  (fn [editor event]
    (-> editor
        (assoc :beep? false)
        (handler event))))

;; -- Tracking the current buffer --------------------------------------------

(defn current-buffer
  "Read or update the current buffer.
  
  This is inteaded to be used with packthread's \"in\" macro, like so:

    (+> editor
        (in e/current-buffer
            (assoc :foo :bar)))
  "
  ([editor]
   (:buffer editor))
  ([editor buffer-fn]
   (update-in editor [:buffer] buffer-fn)))

;; -- Modes ------------------------------------------------------------------

(defn enter-normal-mode
  [editor]
  (assoc editor :mode :normal, :message nil))

(defn mode-middleware
  [mode mode-responder]
  (fn [responder]
    (fn [editor event]
      (if (= mode (:mode editor))
        (mode-responder editor event)
        (responder editor event)))))

;; -- Terminal resizing ------------------------------------------------------

(defn wrap-handle-resize
  [responder]
  (fn [editor [event-type size :as event]]
    (if (= event-type :resize)
      (+> editor
          (assoc-in [:viewport :size] size)
          (in current-buffer
              (b/resize (- (first size) 2))))
      (responder editor event))))

;; -- Exceptions and failures ------------------------------------------------

(defn wrap-handle-exceptions
  [responder]
  (fn [editor event]
    (try
      (responder editor event)
      (catch Throwable e
        (merge editor (ex-data e))))))

;; -- Some "base" responders -------------------------------------------------

(defn beep-responder
  [editor event]
  (beep editor))

(defn unhandled-event-responder
  [editor event]
  (fail (str "Unhandled event " (pr-str event))))

;; -- Movement helpers -------------------------------------------------------

(defn- valid-line?
  [editor i]
  (and (>= i 0)
       (< i (b/line-count (current-buffer editor)))))

(defn change-line
  [editor i-fn]
  (+> editor
      (let [[i] (:cursor (current-buffer editor))
        i (i-fn i)]
        (if-not (valid-line? editor i)
          (beep)
          (in current-buffer
              (b/move-to-line i))))))

;; ---------------------------------------------------------------------------
