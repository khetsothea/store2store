package com.playmoweb.store2store.service;

import com.playmoweb.store2store.dao.IStoreDao;
import com.playmoweb.store2store.utils.CustomObserver;
import com.playmoweb.store2store.utils.Filter;
import com.playmoweb.store2store.utils.SimpleObserver;
import com.playmoweb.store2store.utils.SortingMode;

import java.util.List;

import rx.Observable;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

/**
 * This abstract service hide basic implementation of CRUD operations combined with a storage (dao)
 * @author  Thibaud Giovannetti
 * @by      Playmoweb
 * @date    08/02/2017.
 */
public abstract class AbstractService<T> implements IService<T> {
    /**
     * The type manipulated by the manager
     */
    private final Class<T> clazz;

    /**
     * The storage used by this manager
     */
    private final IStoreDao<T> storage;

    /**
     * A local subscription to handle local observers
     */
    protected final CompositeSubscription subscriptions = new CompositeSubscription();

    /**
     * Public constructor
     * @param clazz
     */
    public AbstractService(Class<T> clazz, IStoreDao<T> storage) {
        this.clazz = clazz;
        this.storage = storage;
    }

    public IStoreDao<T> getStorage() {
        return storage;
    }

    /**************************************************************************
     *   CRUD operations and helpers to simplify usage
     *************************************************************************/

    @Override
    public Observable<List<T>> getAll(final Filter filter, final SortingMode sortingMode, CustomObserver<List<T>> otherSubscriber) {
        Observable<List<T>> observable = getAll(filter, sortingMode)
                .flatMap(new Func1<List<T>, Observable<List<T>>>() {
                    @Override
                    public Observable<List<T>> call(final List<T> ts) {
                        return storage.deleteAll().map(new Func1<Void, List<T>>() {
                            @Override
                            public List<T> call(Void aVoid) {
                                return ts;
                            }
                        });
                    }
                })
                .flatMap(new Func1<List<T>, Observable<List<T>>>() {
                    @Override
                    public Observable<List<T>> call(List<T> ts) {
                        storage.insertOrUpdate(ts);
                        return storage.getAll(filter, sortingMode);
                    }
                });

        subscribeNonNullObserver(observable, otherSubscriber);
        return storage.getAll(filter, sortingMode);
    }

    public final Observable<List<T>> getAll(final CustomObserver<List<T>> otherSubscriber) {
        return getAll(null, SortingMode.DEFAULT, otherSubscriber);
    }

    @Override
    public final Observable<T> getById(final int id, final CustomObserver<T> otherSubscriber) {
        Observable<T> observable = getById(id)
                .flatMap(new Func1<T, Observable<T>>() {
                    @Override
                    public Observable<T> call(T itemFromAsync) {
                        return storage.insertOrUpdate(itemFromAsync);
                    }
                });

        subscribeNonNullObserver(observable, otherSubscriber);
        return storage.getOne(new Filter("id", id), null);
    }

    @Override
    public Observable<T> getOne(Filter filter, SortingMode sortingMode, CustomObserver<T> otherSubscriber) {
        Observable<T> observable = getOne(filter, sortingMode)
                .flatMap(new Func1<T, Observable<T>>() {
                    @Override
                    public Observable<T> call(T itemFromAsync) {
                        return storage.insertOrUpdate(itemFromAsync);
                    }
                });

        subscribeNonNullObserver(observable, otherSubscriber);
        return storage.getOne(filter, sortingMode);
    }

    @Override
    public Observable<T> insert(final T object, CustomObserver<T> otherSubscriber) {
        Observable<T> observable = insert(object)
                .onErrorResumeNext(new Func1<Throwable, Observable<T>>() {
                    @Override
                    public Observable<T> call(final Throwable throwable) {
                        return storage.delete(object).flatMap(new Func1<Void, Observable<T>>() {
                            @Override
                            public Observable<T> call(Void aVoid) {
                                return Observable.error(throwable);
                            }
                        });
                    }
                })
                .flatMap(new Func1<T, Observable<T>>() {
                    @Override
                    public Observable<T> call(T item) {
                        return storage.insertOrUpdate(item);
                    }
                });

        subscribeNonNullObserver(observable, otherSubscriber);
        return storage.insertOrUpdate(object);
    }

    @Override
    public Observable<List<T>> insert(final List<T> objects, CustomObserver<List<T>> otherSubscriber) {
        Observable<List<T>> observable = insert(objects)
                .onErrorResumeNext(new Func1<Throwable, Observable<List<T>>>() {
                    @Override
                    public Observable<List<T>> call(final Throwable throwable) {
                        return storage.delete(objects).flatMap(new Func1<Void, Observable<List<T>>>() {
                            @Override
                            public Observable<List<T>> call(Void aVoid) {
                                return Observable.error(throwable);
                            }
                        });
                    }
                })
                .flatMap(new Func1<List<T>, Observable<List<T>>>() {
                    @Override
                    public Observable<List<T>> call(List<T> items) {
                        return storage.insertOrUpdate(items);
                    }
                });

        subscribeNonNullObserver(observable, otherSubscriber);
        return storage.insertOrUpdate(objects);
    }

    @Override
    public Observable<T> update(final T object, CustomObserver<T> otherSubscriber) {
        Observable<T> observable = update(object)
                .flatMap(new Func1<T, Observable<T>>() {
                    @Override
                    public Observable<T> call(T item) {
                        return storage.insertOrUpdate(item);
                    }
                });

        subscribeNonNullObserver(observable, otherSubscriber);
        return Observable.just(object);
    }

    @Override
    public Observable<List<T>> update(final List<T> objects, CustomObserver<List<T>> otherSubscriber) {
        Observable<List<T>> observable = update(objects)
                .flatMap(new Func1<List<T>, Observable<List<T>>>() {
                    @Override
                    public Observable<List<T>> call(List<T> items) {
                        return storage.insertOrUpdate(items);
                    }
                });

        subscribeNonNullObserver(observable, otherSubscriber);
        return storage.insertOrUpdate(objects);
    }

    @Override
    public Observable<Void> delete(final T object, CustomObserver<Void> otherSubscriber) {
        Observable<Void> observable = delete(object)
                .onErrorResumeNext(new Func1<Throwable, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(final Throwable throwable) {
                        return storage.insertOrUpdate(object).flatMap(new Func1<T, Observable<Void>>() {
                            @Override
                            public Observable<Void> call(T item) {
                                return Observable.error(throwable);
                            }
                        });
                    }
                });

        subscribeNonNullObserver(observable, otherSubscriber);
        return storage.delete(object);
    }

    @Override
    public Observable<Void> delete(final List<T> objects, CustomObserver<Void> otherSubscriber) {
        Observable<Void> observable = delete(objects)
                .onErrorResumeNext(new Func1<Throwable, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(final Throwable throwable) {
                        return storage.insertOrUpdate(objects).flatMap(new Func1<List<T>, Observable<Void>>() {
                            @Override
                            public Observable<Void> call(List<T> items) {
                                return Observable.error(throwable);
                            }
                        });
                    }
                });

        subscribeNonNullObserver(observable, otherSubscriber);
        return storage.delete(objects);
    }

    @Override
    public Observable<Void> deleteAll(CustomObserver<Void> otherSubscriber) {
        Observable<Void> observable = deleteAll()
                .onErrorResumeNext(new Func1<Throwable, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(final Throwable throwable) {
                        // rollback storage state
                        return getAll(null, null).flatMap(new Func1<List<T>, Observable<Void>>() {
                            @Override
                            public Observable<Void> call(List<T> ts) {
                                return storage.insertOrUpdate(ts).flatMap(new Func1<List<T>, Observable<Void>>() {
                                    @Override
                                    public Observable<Void> call(List<T> ts) {
                                        return Observable.error(throwable);
                                    }
                                });
                            }
                        });
                    }
                });

        subscribeNonNullObserver(observable, otherSubscriber);
        return storage.deleteAll();
    }

    /**
     * This method execute an observable only if the subscriber exists.
     *
     * @note The defaults schedulers are io() for subscribeOn and Android.mainThread() for observeOn.
     * @param observable
     * @param otherSubscriber
     * @param <S>
     */
    private <S> void subscribeNonNullObserver(final Observable<S> observable, final CustomObserver<S> otherSubscriber) {
        if(observable != null && otherSubscriber != null) {
            final Subscription s = observable
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribeOn(Schedulers.io())
                    .subscribe(new SimpleObserver<>(otherSubscriber));
            subscriptions.add(s);
        }
    }

    /**************************************************************************
     *   Abstracts protected methods called by operations above
     *************************************************************************/

    /**
     *
     * @param filter
     * @param sortingMode
     * @return
     */
    protected abstract Observable<List<T>> getAll(Filter filter, SortingMode sortingMode);

    /**
     *
     * @param filter
     * @return
     */
    protected abstract Observable<T> getOne(Filter filter, SortingMode sortingMode);

    /**
     *
     * @param id
     * @return
     */
    protected abstract Observable<T> getById(int id);

    /**
     *
     * @param object
     * @return
     */
    protected abstract Observable<T> insert(T object);

    /**
     *
     * @param items
     * @return
     */
    protected abstract Observable<List<T>> insert(List<T> items);

    /**
     *
     * @param object
     * @return
     */
    protected abstract Observable<T> update(T object);

    /**
     *
     * @param items
     * @return
     */
    protected abstract Observable<List<T>> update(List<T> items);

    /**
     *
     * @param items
     */
    protected abstract Observable<Void> delete(List<T> items);

    /**
     *
     * @param object
     */
    protected abstract Observable<Void> delete(T object);

    /**
     * Delete all stored instances
     */
    protected abstract Observable<Void> deleteAll();
}
