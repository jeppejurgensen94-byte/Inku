package eu.kanade.tachiyomi.network;

import java.io.IOException;
import kotlin.coroutines.Continuation;
import okhttp3.Call;
import okhttp3.Response;
import rx.Observable;

public final class OkHttpExtensionsKt {
    private OkHttpExtensionsKt() {
    }

    public static Object await(Call call, Continuation<? super Response> continuation) throws IOException {
        return call.execute();
    }

    public static Object awaitSuccess(Call call, Continuation<? super Response> continuation) throws IOException {
        return RequestsKt.awaitSuccess(call, continuation);
    }

    public static Observable<Response> asObservable(Call call) {
        return Observable.create(subscriber -> {
            try {
                Response response = call.execute();
                if (!subscriber.isUnsubscribed()) {
                    subscriber.onNext(response);
                    subscriber.onCompleted();
                } else {
                    response.close();
                }
            } catch (Throwable error) {
                if (!subscriber.isUnsubscribed()) {
                    subscriber.onError(error);
                }
            }
        });
    }

    public static Observable<Response> asObservableSuccess(Call call) {
        return RequestsKt.asObservableSuccess(call);
    }
}
