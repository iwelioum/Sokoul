<?php
/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Settings'),
        'parent' => 'homepage',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'    => 'homepage',
                'type'  => 'sorter',
                'default' => array(
                    'enabled' => array(
                        'slider'        => __z('Slider'),
                        'featured-post' => __z('Featured titles'),
                        'movies'        => __z('Movies'),
                        'ads'           => __z('Advertising'),
                        'tvshows'       => __z('TV Shows'),
                        'seasons'       => __z('TV Show > Season'),
                        'episodes'      => __z('TV Show > Episode'),
                        'top-imdb'      => __z('TOP IMDb'),
                        'blog'          => __z('Blog entries')
                    ),
                    'disabled' => array(
                        'widgethome'     => __z('Genres Widget'),
                        'slider-movies'  => __z('Slider Movies'),
                        'slider-tvshows' => __z('Slider TV Shows')
                    ),
                ),
                'enabled_title'  => __z('Modules enabled'),
                'disabled_title' => __z('Modules disabled'),
            ),
        )
    )
);
