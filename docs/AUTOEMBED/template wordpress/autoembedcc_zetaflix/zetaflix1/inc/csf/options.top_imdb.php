<?php
/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('TOP IMDb'),
        'parent' => 'homepage',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'      => 'topimdbtitle',
                'type'    => 'text',
                'title'   => __z('Module Title'),
                'default' => __z('TOP IMDb'),
                'subtitle'    => __z('Add title to show')
            ),
            array(
                'id'    => 'topimdblayout',
                'type'  => 'radio',
                'title' => __z('Select Layout'),
                'subtitle'  => __z('Select the type of module to display'),
                'options' => array(
                    'movtv' => __z('Movies and TV Shows'),
                    'movie' => __z('Only Movies'),
                    'tvsho' => __z('Only TV Shows')
                ),
                'default' => 'movtv',
            ),
            array(
                'id'      => 'topimdbrangt',
                'type'    => 'text',
                'title'   => __z('Last months'),
                'subtitle'    => __z('Verify content in the following time range in months'),
                'default' => '12',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:100px'
                )
            ),
            array(
                'id'      => 'topimdbminvt',
                'type'    => 'text',
                'title'   => __z('Minimum votes'),
                'subtitle'    => __z('Set the minimum number of votes so that the content appears in the list'),
                'default' => '100',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:100px'
                )
            ),
            array(
                'id'      => 'topimdbitems',
                'type'    => 'text',
                'title'   => __z('Items number'),
                'subtitle'    => __z('Number of items to show'),
                'default' => '10',
                'attributes' => array(
                    'type' => 'number',
                    'style' => 'width:100px'
                )
            )
        )
    )
);
