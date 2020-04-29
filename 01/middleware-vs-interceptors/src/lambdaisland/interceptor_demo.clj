(ns lambdaisland.interceptor-demo)

;; 1. Middleware pattern

;; handler    :: request map -> response map
;; middleware :: handler -> handler'

;; composition model


(def simple-request {:request-method :get})


(defn my-handler [request]
  ;; some routing logic
  {:status 200
   :body "hello!!"})


(defn print-middleware [handler]
  (fn [request]
    (let [_        (prn :REQUEST request)
          response (handler request)
          _        (prn :RESPONSE response)]
      response)))

(defn add-content-type [handler]
  (fn [request]
    (let [response (handler request)]
      (assoc-in response [:headers "Content-Type"] "text/plain"))))


(def app (-> my-handler add-content-type print-middleware))

(app simple-request)


;; There is a problem with async handlers.
;; We have to write a separate version of our debugging middleware for asynchronous handlers.

(defn debug-middleware [handler]
  (fn [request respond raise]
    (prn :REQUEST request)
    (handler request
             (fn [response]
               (prn :RESPONSE response)
               (respond response))
             raise)))


;; Interceptors pattern
(require '[lambdaisland.interceptor :refer [enqueue execute enter-1 error-1 ctx-summary]])

;; An interceptor is made of three functions (all of them are optional).
;; :enter :: Request  -> Request
;; :leave :: Response -> Response

;; :error :: Request  -> Exception -> Response

;; chain of interceptors [interceptor-1 interceptor-2 ...]
;; queue and stack

;; context
;; {:queue []
;;  :stack []
;;  :request {}
;;  ... }

;; executor


(def debug-interceptor
  {:enter
   (fn [{:keys [request] :as context}]
     (prn :REQUEST request)
     context)
   :leave
   (fn [{:keys [response] :as context}]
     (prn :RESPONSE response)
     context)})

(def handler
  {:enter
   (fn [context]
     (assoc context :response {:status 200 :body "hello!!"}))})


(-> {:request simple-request}
    (enqueue [debug-interceptor handler])
    execute)



(-> {:count 0}
    (enqueue [{:name :add-1
               :enter (fn [ctx] (update ctx :count + 1))}
              {:name :add-10
               :enter (fn [ctx] (update ctx :count + 10))}])
    execute)


(defn make-interceptor [x y]
  {:name  (keyword (str "add-" x "-then-" y))
   :enter (fn [ctx] (update ctx :count + x))
   :leave (fn [ctx] (update ctx :count + y))})


(-> {:count 0}
    (enqueue [(make-interceptor 1 2)
              (make-interceptor 10 20)])
    ctx-summary)


;; The queue is data

(-> {:count 0}
    (enqueue [{:name :enqueue-more
               :enter
               (fn [ctx]
                 (enqueue ctx (repeat 10 (make-interceptor 1 0))))}])
    enter-1
    ctx-summary)


;; Error handling

(defn terminate [ctx]
  (dissoc ctx :lambdaisland.interceptor/queue))


(-> {:count 0}
    (enqueue [{:name :enqueue-more
               :enter
               (fn [ctx]
                 (enqueue ctx (repeat 100
                                      {:name :add-1
                                       :enter
                                       (fn [ctx]
                                         (if (> (:count ctx) 20)
                                           (terminate ctx)
                                           (update ctx :count inc)))})))}])
    execute)


(def boom!
  {:name :BOOM!
   :enter (fn [ctx]
            (throw (ex-info "OOps!" {:very :sorry})))})

(def handle-error
  {:name :handle-error
   :error (fn [ctx ex]
            (update ctx :count * -1))})


(-> {:count 0}
    (enqueue [handle-error
              (make-interceptor 1 2)
              (make-interceptor 10 20)
              boom!
              (make-interceptor 100 200)])
    enter-1
    enter-1
    enter-1
    enter-1

    error-1
    error-1
    error-1
    error-1
    ctx-summary)


;; SUMMARY
;; - Interceptors allow easy mixing of synchronous and asynchronous code.
;; - Interceptors expose the queue and call stack as data, which gives you a fine-grained control over the execution.
;; - Interceptors prevent you from doing error handling with try-catch – not that it would work well with asynchronous code anyway.
;; - Interceptors are probably a bit slower than middleware.
;;

;; Links
;; https://quanttype.net/posts/2018-08-03-why-interceptors.html

;; https://lambdaisland.com/episodes/interceptors-concepts
;; https://github.com/lambdaisland/ep47-interceptors

;; https://lispcast.com/a-model-of-interceptors/
;; https://github.com/fixpoint-clojure/interceptors

;; https://github.com/metosin/sieppari

;; https://github.com/day8/re-frame/blob/master/src/re_frame/interceptor.cljc

